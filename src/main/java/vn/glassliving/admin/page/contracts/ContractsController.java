package vn.glassliving.admin.page.contracts;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.storage.LocalUploadService;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.entity.ContractImage;
import vn.glassliving.contract.repository.ContractImageRepository;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/contracts")
@RequiredArgsConstructor
public class ContractsController {

    private static final List<Short> DURATION_OPTIONS = List.of((short) 3, (short) 6, (short) 12, (short) 24);
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final ContractRepository contractRepository;
    private final ContractImageRepository contractImageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final LocalUploadService localUploadService;
    private final MessageSource messageSource;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails me,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Locale locale,
                       Model model) {
        UUID ownerId = me.getId();
        LocalDate today = LocalDate.now();
        String query = cleanSearch(q);

        List<Room> rooms = ownerRooms(ownerId);
        Map<UUID, Room> roomById = new HashMap<>();
        Map<UUID, Room> roomByTenant = new HashMap<>();
        for (Room room : rooms) {
            roomById.put(room.getId(), room);
            if (room.getCurrentTenantId() != null) {
                roomByTenant.putIfAbsent(room.getCurrentTenantId(), room);
            }
        }

        Map<UUID, Contract> latestContractByTenant = latestContractByTenant(ownerId);

        List<ContractTenantRow> rows = new ArrayList<>();
        for (User tenant : userRepository.findByRolesContaining(User.Role.TENANT)) {
            Contract contract = latestContractByTenant.get(tenant.getId());
            Room assignedRoom = roomByTenant.get(tenant.getId());
            Room contractRoom = contract != null ? roomById.get(contract.getRoomId()) : null;
            Room room = assignedRoom != null ? assignedRoom
                    : (contractRoom != null && tenant.getId().equals(contractRoom.getCurrentTenantId()) ? contractRoom : null);
            if (room == null) {
                continue;
            }
            ContractTenantRow row = new ContractTenantRow(tenant, room, contract, today, messageSource, locale);
            if (query == null || query.isBlank() || row.matches(query)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator
                .comparing(ContractsController::contractRowSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparingInt(ContractTenantRow::sortBucket)
                .thenComparing(ContractTenantRow::getTenantName, String.CASE_INSENSITIVE_ORDER));

        long signed = rows.stream().filter(ContractTenantRow::hasContract).count();
        long missing = rows.stream().filter(r -> !r.hasContract()).count();
        long expiring = rows.stream().filter(ContractTenantRow::isExpiringSoon).count();
        long expired = rows.stream().filter(ContractTenantRow::isExpired).count();
        int sizeSafe = normalizePageSize(size);
        int total = rows.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) sizeSafe);
        int pageSafe = Math.max(0, page);
        if (totalPages > 0) {
            pageSafe = Math.min(pageSafe, totalPages - 1);
        }
        int from = Math.min(pageSafe * sizeSafe, total);
        int to = Math.min(from + sizeSafe, total);
        List<ContractTenantRow> pagedRows = rows.subList(from, to);

        model.addAttribute("activeNav", "contracts");
        model.addAttribute("pageTitle", msg(locale, "contracts.title"));
        model.addAttribute("rows", pagedRows);
        model.addAttribute("filterQ", query);
        model.addAttribute("signedContracts", signed);
        model.addAttribute("missingContracts", missing);
        model.addAttribute("expiringContracts", expiring);
        model.addAttribute("expiredContracts", expired);
        model.addAttribute("totalContracts", total);
        model.addAttribute("currentPage", pageSafe);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("today", today);
        return "admin/contracts";
    }

    private static String cleanSearch(String value) {
        if (value == null) return null;
        String clean = value.trim();
        if (clean.isBlank()
                || "filterQ".equalsIgnoreCase(clean)
                || "q".equalsIgnoreCase(clean)) {
            return null;
        }
        return clean;
    }

    @GetMapping("/{tenantId}")
    public String detail(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID tenantId,
                         Locale locale,
                         Model model) {
        UUID ownerId = me.getId();
        User tenant = tenantOrRedirectTarget(tenantId);
        if (tenant == null) {
            return "redirect:/admin/contracts";
        }

        List<Room> rooms = ownerRooms(ownerId);
        Contract contract = latestContract(ownerId, tenantId);
        Room currentRoom = rooms.stream()
                .filter(r -> tenantId.equals(r.getCurrentTenantId()))
                .findFirst()
                .orElse(null);
        Room selectedRoom = contract != null
                ? rooms.stream().filter(r -> r.getId().equals(contract.getRoomId())).findFirst().orElse(currentRoom)
                : currentRoom;
        if (selectedRoom == null) {
            return "redirect:/admin/contracts";
        }

        LocalDate startDate = contract != null ? contract.getStartDate()
                : (currentRoom != null && currentRoom.getCurrentTenantStartedOn() != null
                ? currentRoom.getCurrentTenantStartedOn()
                : LocalDate.now());
        short duration = contract != null && contract.getDurationMonths() != null
                ? contract.getDurationMonths()
                : (short) 12;
        LocalDate endDate = contract != null ? contract.getEndDate()
                : startDate.plusMonths(duration).minusDays(1);

        model.addAttribute("activeNav", "contracts");
        model.addAttribute("pageTitle", msg(locale, "contracts.detailPageTitle", tenant.getFullName()));
        model.addAttribute("tenant", tenant);
        model.addAttribute("contract", contract);
        model.addAttribute("contractImages", contractImagesFor(contract));
        model.addAttribute("contracts", contractRepository.findByOwnerIdAndTenantUserIdOrderByStartDateDesc(ownerId, tenantId));
        model.addAttribute("rooms", rooms);
        model.addAttribute("currentRoom", currentRoom);
        model.addAttribute("selectedRoom", selectedRoom);
        model.addAttribute("durationOptions", DURATION_OPTIONS);
        model.addAttribute("selectedDuration", duration);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("today", LocalDate.now());
        return "admin/contract-detail";
    }

    @PostMapping("/{tenantId}")
    @Transactional
    public String save(@AuthenticationPrincipal AppUserDetails me,
                       @PathVariable UUID tenantId,
                       @RequestParam(required = false) UUID roomId,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                       @RequestParam(defaultValue = "12") short durationMonths,
                       @RequestParam(required = false) List<MultipartFile> contractImages,
                       @RequestParam(required = false) List<UUID> removeContractImageIds,
                       @RequestParam(defaultValue = "false") boolean removeContractImage,
                       Locale locale,
                       RedirectAttributes ra) {
        UUID ownerId = me.getId();
        try {
            User tenant = tenantOrThrow(tenantId, locale);
            Contract contract = latestContract(ownerId, tenantId);
            Room room = resolveRoom(ownerId, tenantId, roomId, contract, locale);
            if (!DURATION_OPTIONS.contains(durationMonths)) {
                throw BusinessException.badRequest(msg(locale, "contracts.detail.validation.durationInvalid"));
            }
            if (startDate == null) {
                throw BusinessException.badRequest(msg(locale, "contracts.detail.validation.startDateRequired"));
            }
            LocalDate finalEndDate = endDate != null ? endDate : startDate.plusMonths(durationMonths).minusDays(1);
            if (!finalEndDate.isAfter(startDate)) {
                throw BusinessException.badRequest(msg(locale, "contracts.detail.validation.endAfterStart"));
            }

            if (startDate.isAfter(LocalDate.now().plusYears(5))) {
                throw BusinessException.badRequest(msg(locale, "contracts.detail.validation.startTooFar"));
            }

            if (contract == null) {
                contract = Contract.builder()
                        .code(nextContractCode())
                        .ownerId(ownerId)
                        .tenantUserId(tenant.getId())
                        .billingDay((short) 1)
                        .depositAmount(nz(room.getDepositAmount()))
                        .rentMonthly(nz(room.getPriceMonthly()))
                        .serviceFee(nz(room.getServiceFee()))
                        .electricUnit(nz(room.getElectricUnit(), new BigDecimal("4000")))
                        .waterUnit(nz(room.getWaterUnit(), new BigDecimal("25000")))
                        .build();
            } else {
                clearOldRoomIfChanged(ownerId, tenantId, contract.getRoomId(), room.getId());
            }

            contract.setRoomId(room.getId());
            contract.setStartDate(startDate);
            contract.setEndDate(finalEndDate);
            contract.setDurationMonths(durationMonths);
            contract.setRentMonthly(nz(room.getPriceMonthly()));
            contract.setDepositAmount(nz(room.getDepositAmount()));
            contract.setServiceFee(nz(room.getServiceFee()));
            contract.setElectricUnit(nz(room.getElectricUnit(), new BigDecimal("4000")));
            contract.setWaterUnit(nz(room.getWaterUnit(), new BigDecimal("25000")));
            contract.setStatus(statusFor(finalEndDate));
            contract = contractRepository.saveAndFlush(contract);
            syncContractImages(ownerId, contract, contractImages, removeContractImage, removeContractImageIds);
            contractRepository.save(contract);

            room.setStatus(Room.RoomStatus.OCCUPIED);
            room.setCurrentTenantId(tenantId);
            room.setCurrentTenantStartedOn(startDate);
            if (room.getCurrentTenantPaidUntil() == null || room.getCurrentTenantPaidUntil().isBefore(startDate)) {
                room.setCurrentTenantPaidUntil(startDate.plusMonths(1).minusDays(1));
            }
            roomRepository.save(room);

            FlashAlert.ok(ra, msg(locale, "contracts.detail.flash.saved", tenant.getFullName()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/contracts/" + tenantId;
    }

    @PostMapping("/{tenantId}/delete")
    @Transactional
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID tenantId,
                         Locale locale,
                         RedirectAttributes ra) {
        UUID ownerId = me.getId();
        Contract contract = latestContract(ownerId, tenantId);
        if (contract == null) {
            FlashAlert.err(ra, msg(locale, "contracts.detail.flash.notFoundToDelete"));
            return "redirect:/admin/contracts/" + tenantId;
        }
        deleteContractImages(contract);
        contractRepository.delete(contract);
        FlashAlert.ok(ra, msg(locale, "contracts.detail.flash.deleted", contract.getCode()));
        return "redirect:/admin/contracts";
    }

    private User tenantOrRedirectTarget(UUID tenantId) {
        return userRepository.findById(tenantId)
                .filter(u -> u.getRoles().contains(User.Role.TENANT))
                .orElse(null);
    }

    private User tenantOrThrow(UUID tenantId, Locale locale) {
        return userRepository.findById(tenantId)
                .filter(u -> u.getRoles().contains(User.Role.TENANT))
                .orElseThrow(() -> BusinessException.notFound(msg(locale, "tenants.tenant")));
    }

    private List<Room> ownerRooms(UUID ownerId) {
        return roomRepository.findByOwnerId(ownerId,
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "code"))).getContent();
    }

    private Contract latestContract(UUID ownerId, UUID tenantId) {
        List<Contract> contracts = contractRepository.findByOwnerIdAndTenantUserIdOrderByStartDateDesc(ownerId, tenantId);
        return contracts.isEmpty() ? null : contracts.get(0);
    }

    private Map<UUID, Contract> latestContractByTenant(UUID ownerId) {
        Map<UUID, Contract> latest = new HashMap<>();
        contractRepository.findByOwnerId(ownerId, PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "startDate")))
                .getContent()
                .forEach(c -> latest.putIfAbsent(c.getTenantUserId(), c));
        return latest;
    }

    private static OffsetDateTime contractRowSortTime(ContractTenantRow row) {
        if (row.getContract() != null && row.getContract().getCreatedAt() != null) {
            return row.getContract().getCreatedAt();
        }
        return row.getTenant() != null ? row.getTenant().getCreatedAt() : null;
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }

    private Room resolveRoom(UUID ownerId, UUID tenantId, UUID requestedRoomId, Contract contract, Locale locale) {
        UUID effectiveRoomId = requestedRoomId != null ? requestedRoomId : (contract != null ? contract.getRoomId() : null);
        if (effectiveRoomId == null) {
            throw BusinessException.badRequest(msg(locale, "contracts.detail.validation.roomRequired"));
        }
        Room room = roomRepository.findById(effectiveRoomId)
                .orElseThrow(() -> BusinessException.notFound(msg(locale, "tenants.room")));
        if (!ownerId.equals(room.getOwnerId())) {
            throw BusinessException.forbidden(msg(locale, "contracts.detail.validation.roomForbidden"));
        }
        if (room.getCurrentTenantId() != null && !tenantId.equals(room.getCurrentTenantId())) {
            throw BusinessException.conflict(msg(locale, "contracts.detail.validation.roomOccupied"));
        }
        return room;
    }

    private List<ContractImage> contractImagesFor(Contract contract) {
        if (contract == null || contract.getId() == null) {
            return List.of();
        }
        List<ContractImage> images = contractImageRepository.findByContractIdOrderBySortOrderAsc(contract.getId());
        if (images.isEmpty() && contract.getContractImageUrl() != null && !contract.getContractImageUrl().isBlank()) {
            return List.of(ContractImage.builder()
                    .contractId(contract.getId())
                    .url(contract.getContractImageUrl())
                    .alt(contract.getCode())
                    .sortOrder(0)
                    .build());
        }
        return images;
    }

    private void syncContractImages(UUID ownerId,
                                    Contract contract,
                                    List<MultipartFile> uploads,
                                    boolean removeAll,
                                    List<UUID> removeIds) {
        List<ContractImage> existing = contractImageRepository.findByContractIdOrderBySortOrderAsc(contract.getId());
        if (removeAll) {
            for (ContractImage image : existing) {
                localUploadService.deletePublicUrl(image.getUrl());
            }
            contractImageRepository.deleteAll(existing);
            existing = List.of();
            if (contract.getContractImageUrl() != null) {
                localUploadService.deletePublicUrl(contract.getContractImageUrl());
            }
        } else if (removeIds != null && !removeIds.isEmpty()) {
            Set<UUID> requestedIds = Set.copyOf(removeIds);
            List<ContractImage> deleting = existing.stream()
                    .filter(image -> requestedIds.contains(image.getId()))
                    .toList();
            for (ContractImage image : deleting) {
                localUploadService.deletePublicUrl(image.getUrl());
            }
            contractImageRepository.deleteAll(deleting);
            existing = existing.stream()
                    .filter(image -> !requestedIds.contains(image.getId()))
                    .toList();
        }

        int nextSort = existing.stream()
                .map(ContractImage::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        if (uploads != null) {
            for (MultipartFile upload : uploads) {
                if (upload == null || upload.isEmpty()) {
                    continue;
                }
                String url = localUploadService.storeImage(upload, "contracts/" + ownerId + "/" + contract.getId(), "contract");
                ContractImage image = ContractImage.builder()
                        .contractId(contract.getId())
                        .url(url)
                        .alt(contract.getCode())
                        .sortOrder(nextSort++)
                        .build();
                contractImageRepository.save(image);
            }
        }

        refreshContractImageUrl(contract);
    }

    private void refreshContractImageUrl(Contract contract) {
        List<ContractImage> images = contractImageRepository.findByContractIdOrderBySortOrderAsc(contract.getId());
        contract.setContractImageUrl(images.isEmpty() ? null : images.get(0).getUrl());
    }

    private void deleteContractImages(Contract contract) {
        List<ContractImage> images = contractImageRepository.findByContractIdOrderBySortOrderAsc(contract.getId());
        for (ContractImage image : images) {
            localUploadService.deletePublicUrl(image.getUrl());
        }
        contractImageRepository.deleteAll(images);
        if (images.isEmpty()) {
            localUploadService.deletePublicUrl(contract.getContractImageUrl());
        }
    }

    private void clearOldRoomIfChanged(UUID ownerId, UUID tenantId, UUID oldRoomId, UUID newRoomId) {
        if (oldRoomId == null || Objects.equals(oldRoomId, newRoomId)) {
            return;
        }
        roomRepository.findById(oldRoomId)
                .filter(r -> ownerId.equals(r.getOwnerId()))
                .filter(r -> tenantId.equals(r.getCurrentTenantId()))
                .ifPresent(r -> {
                    r.setStatus(Room.RoomStatus.AVAILABLE);
                    r.setCurrentTenantId(null);
                    r.setCurrentTenantStartedOn(null);
                    r.setCurrentTenantPaidUntil(null);
                    roomRepository.save(r);
                });
    }

    private String nextContractCode() {
        String prefix = "HD-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        for (int i = 0; i < 20; i++) {
            String code = prefix + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            if (!contractRepository.existsByCode(code)) {
                return code;
            }
        }
        return prefix + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    private static Contract.ContractStatus statusFor(LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (endDate.isBefore(today)) {
            return Contract.ContractStatus.EXPIRED;
        }
        if (!endDate.isAfter(today.plusDays(30))) {
            return Contract.ContractStatus.EXPIRING_SOON;
        }
        return Contract.ContractStatus.ACTIVE;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private static String normalize(String value) {
        String text = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9@.\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static class ContractTenantRow {
        private final User tenant;
        private final Room room;
        private final Contract contract;
        private final LocalDate today;
        private final MessageSource messageSource;
        private final Locale locale;

        ContractTenantRow(User tenant, Room room, Contract contract, LocalDate today,
                          MessageSource messageSource, Locale locale) {
            this.tenant = tenant;
            this.room = room;
            this.contract = contract;
            this.today = today;
            this.messageSource = messageSource;
            this.locale = locale;
        }

        public User getTenant() { return tenant; }
        public Room getRoom() { return room; }
        public Contract getContract() { return contract; }
        public String getTenantName() { return Objects.toString(tenant.getFullName(), ""); }
        public boolean hasContract() { return contract != null; }
        public boolean isExpired() { return contract != null && contract.getEndDate().isBefore(today); }
        public boolean isExpiringSoon() {
            return contract != null && !isExpired() && !contract.getEndDate().isAfter(today.plusDays(30));
        }

        public int sortBucket() {
            if (!hasContract()) return 0;
            if (isExpired()) return 1;
            if (isExpiringSoon()) return 2;
            return 3;
        }

        public String getStatusLabel() {
            if (contract == null) return msg("contracts.noContract");
            if (contract.getStatus() == Contract.ContractStatus.TERMINATED) return msg("contracts.status.terminated");
            if (isExpired()) return msg("contracts.expired");
            if (isExpiringSoon()) return msg("contracts.expiringSoon");
            return msg("contracts.status.active");
        }

        public String getStatusClass() {
            if (contract == null) return "badge-mute";
            if (contract.getStatus() == Contract.ContractStatus.TERMINATED) return "badge-mute";
            if (isExpired()) return "badge-rose";
            if (isExpiringSoon()) return "badge-amber";
            return "badge-emerald";
        }

        public String getDaysLeftLabel() {
            if (contract == null) return msg("tenants.notSet");
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getEndDate());
            if (days < 0) return msg("contracts.overdueDays", Math.abs(days));
            if (days == 0) return msg("contracts.expiredToday");
            return msg("contracts.daysLeft", days);
        }

        public boolean matches(String query) {
            String needle = normalize(query);
            String haystack = normalize(String.join(" ",
                    Objects.toString(tenant.getFullName(), ""),
                    Objects.toString(tenant.getEmail(), ""),
                    Objects.toString(tenant.getPhone(), ""),
                    room != null ? Objects.toString(room.getCode(), "") : "",
                    room != null ? Objects.toString(room.getTitle(), "") : "",
                    contract != null ? Objects.toString(contract.getCode(), "") : ""));
            return haystack.contains(needle);
        }

        private String msg(String code, Object... args) {
            return messageSource.getMessage(code, args, locale);
        }
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
