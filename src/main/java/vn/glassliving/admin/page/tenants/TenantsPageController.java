package vn.glassliving.admin.page.tenants;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
public class TenantsPageController {

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final MessageSource messageSource;

    @GetMapping
    public String tenants(@AuthenticationPrincipal AppUserDetails me,
                          @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          Locale locale,
                          Model model) {
        UUID ownerId = me.getId();
        LocalDate today = LocalDate.now();
        String query = cleanSearch(q);

        List<Room> assignableRooms = roomRepository.findByOwnerId(ownerId,
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "code"))).getContent();
        Map<UUID, Property> propertyById = propertyRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
                .collect(java.util.stream.Collectors.toMap(Property::getId, property -> property));
        Map<UUID, Room> tenantCurrentRoomByTenantId = new HashMap<>();
        Map<UUID, Property> tenantCurrentPropertyByTenantId = new HashMap<>();
        for (Room room : assignableRooms) {
            if (room.getCurrentTenantId() != null) {
                tenantCurrentRoomByTenantId.putIfAbsent(room.getCurrentTenantId(), room);
                Property property = propertyById.get(room.getPropertyId());
                if (property != null) {
                    tenantCurrentPropertyByTenantId.putIfAbsent(room.getCurrentTenantId(), property);
                }
            }
        }

        List<Invoice> ownerInvoices = invoiceRepository.findByOwnerId(ownerId,
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "issueDate"))).getContent();
        Map<UUID, List<Invoice>> tenantRoomInvoicesByTenantId = new HashMap<>();
        Map<UUID, Invoice> tenantLatestInvoiceByTenantId = new HashMap<>();
        Map<UUID, Long> tenantOpenInvoiceCountByTenantId = new HashMap<>();
        Map<UUID, BigDecimal> tenantOpenInvoiceAmountByTenantId = new HashMap<>();
        for (Invoice invoice : ownerInvoices) {
            UUID tenantId = invoice.getTenantUserId();
            Room currentRoom = tenantCurrentRoomByTenantId.get(tenantId);
            boolean belongsToCurrentRoom = currentRoom != null && currentRoom.getId().equals(invoice.getRoomId());
            if (!belongsToCurrentRoom) {
                continue;
            }

            tenantRoomInvoicesByTenantId.computeIfAbsent(tenantId, ignored -> new ArrayList<>()).add(invoice);
            tenantLatestInvoiceByTenantId.putIfAbsent(tenantId, invoice);
            if (isOpenInvoice(invoice)) {
                tenantOpenInvoiceCountByTenantId.merge(tenantId, 1L, Long::sum);
                tenantOpenInvoiceAmountByTenantId.merge(tenantId,
                        invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO,
                        BigDecimal::add);
            }
        }

        List<User> users = new ArrayList<>(userRepository.findByRolesContaining(User.Role.TENANT));
        int allTenantsCount = users.size();
        List<UUID> allTenantIds = users.stream().map(User::getId).toList();

        if (query != null && !query.isBlank()) {
            users = users.stream()
                    .filter(u -> matchesTenantSearch(u, tenantCurrentRoomByTenantId.get(u.getId()), query))
                    .toList();
        }

        Comparator<User> newestFirst = Comparator
                .comparing(User::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed();
        if (query != null && !query.isBlank()) {
            users = users.stream()
                    .sorted(Comparator
                            .comparingInt((User u) -> tenantSearchScore(u, tenantCurrentRoomByTenantId.get(u.getId()), query))
                            .thenComparing(newestFirst))
                    .toList();
        } else {
            users = users.stream().sorted(newestFirst).toList();
        }

        int sizeSafe = normalizePageSize(size);
        int total = users.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) sizeSafe);
        int pageSafe = Math.max(0, page);
        if (totalPages > 0) {
            pageSafe = Math.min(pageSafe, totalPages - 1);
        }
        int from = Math.min(pageSafe * sizeSafe, total);
        int to = Math.min(from + sizeSafe, total);
        List<User> slice = users.subList(from, to);

        long assignedTenants = tenantCurrentRoomByTenantId.keySet().stream()
                .filter(allTenantIds::contains)
                .count();
        long overdueRooms = assignableRooms.stream()
                .filter(r -> r.getCurrentTenantId() != null)
                .filter(r -> r.getCurrentTenantPaidUntil() != null && r.getCurrentTenantPaidUntil().isBefore(today))
                .count();
        long dueSoonRooms = assignableRooms.stream()
                .filter(r -> r.getCurrentTenantId() != null)
                .filter(r -> r.getCurrentTenantPaidUntil() != null
                        && !r.getCurrentTenantPaidUntil().isBefore(today)
                        && !r.getCurrentTenantPaidUntil().isAfter(today.plusDays(7)))
                .count();
        long missingPaidUntilRooms = assignableRooms.stream()
                .filter(r -> r.getCurrentTenantId() != null)
                .filter(r -> r.getCurrentTenantPaidUntil() == null)
                .count();

        model.addAttribute("activeNav", "tenants");
        model.addAttribute("pageTitle", msg(locale, "tenants.title"));
        model.addAttribute("tenants", slice);
        model.addAttribute("assignableRooms", assignableRooms);
        model.addAttribute("tenantCurrentRoomByTenantId", tenantCurrentRoomByTenantId);
        model.addAttribute("tenantCurrentPropertyByTenantId", tenantCurrentPropertyByTenantId);
        model.addAttribute("tenantRoomInvoicesByTenantId", tenantRoomInvoicesByTenantId);
        model.addAttribute("tenantLatestInvoiceByTenantId", tenantLatestInvoiceByTenantId);
        model.addAttribute("tenantOpenInvoiceCountByTenantId", tenantOpenInvoiceCountByTenantId);
        model.addAttribute("tenantOpenInvoiceAmountByTenantId", tenantOpenInvoiceAmountByTenantId);
        model.addAttribute("suggestedPaidUntil", LocalDate.now().plusMonths(1).minusDays(1));
        model.addAttribute("today", today);
        model.addAttribute("zeroAmount", BigDecimal.ZERO);
        model.addAttribute("allTenantsCount", allTenantsCount);
        model.addAttribute("totalTenants", total);
        model.addAttribute("assignedTenants", assignedTenants);
        model.addAttribute("unassignedTenants", Math.max(0, allTenantsCount - assignedTenants));
        model.addAttribute("dueSoonRooms", dueSoonRooms);
        model.addAttribute("overdueRooms", overdueRooms);
        model.addAttribute("missingPaidUntilRooms", missingPaidUntilRooms);
        model.addAttribute("currentPage", pageSafe);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("filterQ", query);
        return "admin/tenants";
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
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

    @GetMapping("/{id}")
    public String tenantDetail(@AuthenticationPrincipal AppUserDetails me,
                               @PathVariable UUID id,
                               Locale locale,
                               Model model) {
        User tenant = userRepository.findById(id).orElse(null);
        if (tenant == null || !tenant.getRoles().contains(User.Role.TENANT)) {
            return "redirect:/admin/tenants";
        }

        List<Invoice> tenantInvoices = invoiceRepository.findByTenantUserId(id,
                        PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "issueDate")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt")))).getContent()
                .stream()
                .filter(invoice -> me.getId().equals(invoice.getOwnerId()))
                .toList();
        long openInvoiceCount = tenantInvoices.stream().filter(TenantsPageController::isOpenInvoice).count();
        BigDecimal openInvoiceAmount = tenantInvoices.stream()
                .filter(TenantsPageController::isOpenInvoice)
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Room> currentRooms = roomRepository.findByOwnerId(me.getId(), PageRequest.of(0, 200))
                .getContent().stream().filter(room -> id.equals(room.getCurrentTenantId())).toList();
        Room currentRoom = currentRooms.isEmpty() ? null : currentRooms.get(0);
        List<Room> assignableRooms = roomRepository.findByOwnerId(me.getId(),
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "code"))).getContent();
        List<Room> selectableRooms = assignableRooms.stream()
                .filter(room -> room.getCurrentTenantId() == null || id.equals(room.getCurrentTenantId()))
                .toList();
        List<Room> occupiedRooms = assignableRooms.stream()
                .filter(room -> room.getCurrentTenantId() != null && !id.equals(room.getCurrentTenantId()))
                .toList();
        Property currentProperty = currentRoom != null
                ? propertyRepository.findById(currentRoom.getPropertyId())
                        .filter(property -> me.getId().equals(property.getOwnerId()))
                        .orElse(null)
                : null;
        List<Contract> tenantContracts = contractRepository.findByOwnerIdAndTenantUserIdOrderByStartDateDesc(me.getId(), id);
        Contract latestContract = tenantContracts.isEmpty() ? null : tenantContracts.get(0);

        model.addAttribute("activeNav", "tenants");
        model.addAttribute("pageTitle", msg(locale, "tenants.detailPageTitle", tenant.getFullName()));
        model.addAttribute("tenant", tenant);
        model.addAttribute("invoices", tenantInvoices);
        model.addAttribute("currentRooms", currentRooms);
        model.addAttribute("currentRoom", currentRoom);
        model.addAttribute("currentProperty", currentProperty);
        model.addAttribute("latestContract", latestContract);
        model.addAttribute("assignableRooms", assignableRooms);
        model.addAttribute("selectableRooms", selectableRooms);
        model.addAttribute("occupiedRooms", occupiedRooms);
        model.addAttribute("suggestedPaidUntil", LocalDate.now().plusMonths(1).minusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("openInvoiceCount", openInvoiceCount);
        model.addAttribute("openInvoiceAmount", openInvoiceAmount);
        return "admin/tenant-detail";
    }

    private static boolean isOpenInvoice(Invoice invoice) {
        return invoice != null && (invoice.getStatus() == Invoice.InvoiceStatus.PENDING
                || invoice.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID
                || invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE);
    }

    private static boolean matchesTenantSearch(User user, Room room, String rawQuery) {
        String needle = normalizeSearch(rawQuery);
        String phoneNeedle = digitsOnly(rawQuery);
        if (needle.isBlank() && phoneNeedle.isBlank()) {
            return true;
        }

        String blob = tenantSearchBlob(user, room);
        if (!needle.isBlank() && blob.contains(needle)) {
            return true;
        }
        if (!phoneNeedle.isBlank() && digitsOnly(user.getPhone()).contains(phoneNeedle)) {
            return true;
        }
        return tokensAllMatch(blob, needle);
    }

    private static int tenantSearchScore(User user, Room room, String rawQuery) {
        String needle = normalizeSearch(rawQuery);
        String phoneNeedle = digitsOnly(rawQuery);
        String name = normalizeSearch(user.getFullName());
        String email = normalizeSearch(user.getEmail());
        String phone = digitsOnly(user.getPhone());
        String roomCode = room != null ? normalizeSearch(room.getCode()) : "";
        String roomTitle = room != null ? normalizeSearch(room.getTitle()) : "";
        String blob = tenantSearchBlob(user, room);

        if (!needle.isBlank() && (name.equals(needle) || email.equals(needle)
                || roomCode.equals(needle) || roomTitle.equals(needle))) {
            return 0;
        }
        if (!phoneNeedle.isBlank() && phone.equals(phoneNeedle)) {
            return 0;
        }
        if (!needle.isBlank() && (name.startsWith(needle) || email.startsWith(needle)
                || roomCode.startsWith(needle) || roomTitle.startsWith(needle))) {
            return 1;
        }
        if (!phoneNeedle.isBlank() && phone.startsWith(phoneNeedle)) {
            return 1;
        }
        if (tokensAllMatch(name, needle) || tokensAllMatch(email, needle)
                || tokensAllMatch(roomCode + " " + roomTitle, needle)) {
            return 2;
        }
        if (!needle.isBlank() && blob.contains(needle)) {
            return 3;
        }
        if (tokensAllMatch(blob, needle)) {
            return 4;
        }
        if (!phoneNeedle.isBlank() && phone.contains(phoneNeedle)) {
            return 5;
        }
        return 99;
    }

    private static String tenantSearchBlob(User user, Room room) {
        return normalizeSearch(String.join(" ",
                Objects.toString(user.getFullName(), ""),
                Objects.toString(user.getEmail(), ""),
                Objects.toString(user.getPhone(), ""),
                room != null ? Objects.toString(room.getCode(), "") : "",
                room != null ? Objects.toString(room.getTitle(), "") : "",
                room != null ? Objects.toString(room.getDistrict(), "") : "",
                room != null ? Objects.toString(room.getCity(), "") : ""));
    }

    private static boolean tokensAllMatch(String haystack, String rawNeedle) {
        if (rawNeedle == null || rawNeedle.isBlank()) {
            return false;
        }
        String[] tokens = rawNeedle.split("\\s+");
        boolean hasToken = false;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            hasToken = true;
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return hasToken;
    }

    private static String normalizeSearch(String value) {
        String text = Objects.toString(value, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replace('đ', 'd').replace('Đ', 'd')
                .replaceAll("[^a-z0-9@.\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String digitsOnly(String value) {
        return Objects.toString(value, "").replaceAll("\\D+", "");
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
