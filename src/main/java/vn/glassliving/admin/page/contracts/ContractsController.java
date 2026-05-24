package vn.glassliving.admin.page.contracts;

import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.contract.entity.Contract;
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

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails me,
                       @RequestParam(required = false) String q,
                       Model model) {
        UUID ownerId = me.getId();
        LocalDate today = LocalDate.now();

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
            Room room = contract != null ? roomById.get(contract.getRoomId()) : roomByTenant.get(tenant.getId());
            ContractTenantRow row = new ContractTenantRow(tenant, room, contract, today);
            if (q == null || q.isBlank() || row.matches(q)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator
                .comparingInt(ContractTenantRow::sortBucket)
                .thenComparing(ContractTenantRow::getTenantName, String.CASE_INSENSITIVE_ORDER));

        long signed = rows.stream().filter(ContractTenantRow::hasContract).count();
        long missing = rows.stream().filter(r -> !r.hasContract()).count();
        long expiring = rows.stream().filter(ContractTenantRow::isExpiringSoon).count();
        long expired = rows.stream().filter(ContractTenantRow::isExpired).count();

        model.addAttribute("activeNav", "contracts");
        model.addAttribute("pageTitle", "Hợp đồng");
        model.addAttribute("rows", rows);
        model.addAttribute("filterQ", q);
        model.addAttribute("signedContracts", signed);
        model.addAttribute("missingContracts", missing);
        model.addAttribute("expiringContracts", expiring);
        model.addAttribute("expiredContracts", expired);
        model.addAttribute("today", today);
        return "admin/contracts";
    }

    @GetMapping("/{tenantId}")
    public String detail(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID tenantId,
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
        model.addAttribute("pageTitle", "Hợp đồng: " + tenant.getFullName());
        model.addAttribute("tenant", tenant);
        model.addAttribute("contract", contract);
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
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                       @RequestParam(defaultValue = "12") short durationMonths,
                       RedirectAttributes ra) {
        UUID ownerId = me.getId();
        try {
            User tenant = tenantOrThrow(tenantId);
            Contract contract = latestContract(ownerId, tenantId);
            Room room = resolveRoom(ownerId, tenantId, roomId, contract);
            if (!DURATION_OPTIONS.contains(durationMonths)) {
                throw BusinessException.badRequest("Thời hạn hợp đồng phải là 3, 6, 12 hoặc 24 tháng.");
            }
            LocalDate finalEndDate = endDate != null ? endDate : startDate.plusMonths(durationMonths).minusDays(1);
            if (!finalEndDate.isAfter(startDate)) {
                throw BusinessException.badRequest("Ngày kết thúc hợp đồng phải sau ngày bắt đầu.");
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
            contractRepository.save(contract);

            room.setStatus(Room.RoomStatus.OCCUPIED);
            room.setCurrentTenantId(tenantId);
            room.setCurrentTenantStartedOn(startDate);
            if (room.getCurrentTenantPaidUntil() == null || room.getCurrentTenantPaidUntil().isBefore(startDate)) {
                room.setCurrentTenantPaidUntil(startDate.plusMonths(1).minusDays(1));
            }
            roomRepository.save(room);

            FlashAlert.ok(ra, "Đã lưu hợp đồng cho " + tenant.getFullName() + ".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/contracts/" + tenantId;
    }

    private User tenantOrRedirectTarget(UUID tenantId) {
        return userRepository.findById(tenantId)
                .filter(u -> u.getRoles().contains(User.Role.TENANT))
                .orElse(null);
    }

    private User tenantOrThrow(UUID tenantId) {
        return userRepository.findById(tenantId)
                .filter(u -> u.getRoles().contains(User.Role.TENANT))
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
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

    private Room resolveRoom(UUID ownerId, UUID tenantId, UUID requestedRoomId, Contract contract) {
        UUID effectiveRoomId = requestedRoomId != null ? requestedRoomId : (contract != null ? contract.getRoomId() : null);
        if (effectiveRoomId == null) {
            throw BusinessException.badRequest("Hãy chọn phòng áp dụng cho hợp đồng.");
        }
        Room room = roomRepository.findById(effectiveRoomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!ownerId.equals(room.getOwnerId())) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        if (room.getCurrentTenantId() != null && !tenantId.equals(room.getCurrentTenantId())) {
            throw BusinessException.conflict("Phòng này đang có khách khác ở. Hãy chọn phòng khác hoặc gỡ khách khỏi phòng trước.");
        }
        return room;
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

        ContractTenantRow(User tenant, Room room, Contract contract, LocalDate today) {
            this.tenant = tenant;
            this.room = room;
            this.contract = contract;
            this.today = today;
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
            if (contract == null) return "Chưa có hợp đồng";
            if (contract.getStatus() == Contract.ContractStatus.TERMINATED) return "Đã kết thúc";
            if (isExpired()) return "Hết hạn";
            if (isExpiringSoon()) return "Sắp hết hạn";
            return "Đang hiệu lực";
        }

        public String getStatusClass() {
            if (contract == null) return "badge-mute";
            if (contract.getStatus() == Contract.ContractStatus.TERMINATED) return "badge-mute";
            if (isExpired()) return "badge-rose";
            if (isExpiringSoon()) return "badge-amber";
            return "badge-emerald";
        }

        public String getDaysLeftLabel() {
            if (contract == null) return "Chưa thiết lập";
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getEndDate());
            if (days < 0) return "Quá hạn " + Math.abs(days) + " ngày";
            if (days == 0) return "Hết hạn hôm nay";
            return "Còn " + days + " ngày";
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
    }
}
