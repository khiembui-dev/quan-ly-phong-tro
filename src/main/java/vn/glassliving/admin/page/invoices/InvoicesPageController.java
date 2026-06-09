package vn.glassliving.admin.page.invoices;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoicesPageController {

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @GetMapping
    public String invoices(@AuthenticationPrincipal AppUserDetails me,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) UUID propertyId,
                           @RequestParam(required = false) UUID roomId,
                           @RequestParam(required = false) String month,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           Locale locale,
                           Model model) {
        UUID ownerId = me.getId();
        invoiceService.refreshAgingStatuses(ownerId);

        Invoice.InvoiceStatus parsedStatus = parseStatus(status);
        String statusValue = parsedStatus != null ? parsedStatus.name() : "";
        String query = cleanSearch(q);
        int sizeSafe = normalizePageSize(size);
        Pageable pageable = PageRequest.of(Math.max(0, page), sizeSafe);

        List<Property> ownerProperties = propertyRepository.findByOwnerIdOrderByNameAsc(ownerId);
        Set<UUID> ownerPropertyIds = ownerProperties.stream()
                .map(Property::getId)
                .collect(Collectors.toSet());
        UUID filterPropertyId = propertyId != null && ownerPropertyIds.contains(propertyId) ? propertyId : null;

        List<Room> ownerRooms = roomRepository.findByOwnerId(
                ownerId,
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.ASC, "code", "title"))
        ).getContent();
        Map<UUID, Room> ownerRoomById = ownerRooms.stream()
                .collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a));
        UUID filterRoomId = roomId != null && ownerRoomById.containsKey(roomId) ? roomId : null;
        if (filterPropertyId != null && filterRoomId != null) {
            Room selectedRoom = ownerRoomById.get(filterRoomId);
            if (selectedRoom == null || !filterPropertyId.equals(selectedRoom.getPropertyId())) {
                filterRoomId = null;
            }
        }

        YearMonth filterMonth = parseMonth(month);
        String filterError = null;
        String datePriorityWarning = null;
        boolean hasDateRange = fromDate != null || toDate != null;

        Page<Invoice> invoicePage;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            filterError = msg(locale, "invoices.validation.dateRange");
            invoicePage = new PageImpl<>(List.of(), pageable, 0);
        } else {
            if (filterMonth != null && hasDateRange) {
                datePriorityWarning = msg(locale, "invoices.datePriorityWarning");
            }
            invoicePage = invoiceService.findAdminInvoices(
                    ownerId,
                    query,
                    filterPropertyId,
                    filterRoomId,
                    parsedStatus,
                    filterMonth,
                    fromDate,
                    toDate,
                    pageable
            );
        }

        Map<UUID, Room> roomById = loadRooms(invoicePage.getContent(), ownerRoomById);
        Map<UUID, User> tenantById = loadTenants(invoicePage.getContent());
        Map<UUID, Property> propertyById = ownerProperties.stream()
                .collect(Collectors.toMap(Property::getId, Function.identity(), (a, b) -> a));

        model.addAttribute("activeNav", "invoices");
        model.addAttribute("pageTitle", msg(locale, "invoices.title"));
        model.addAttribute("invoicePage", invoicePage);
        model.addAttribute("invoices", invoicePage.getContent());
        model.addAttribute("roomById", roomById);
        model.addAttribute("tenantById", tenantById);
        model.addAttribute("propertyById", propertyById);
        model.addAttribute("properties", ownerProperties);
        model.addAttribute("rooms", ownerRooms);
        model.addAttribute("filter", statusValue);
        model.addAttribute("q", query);
        model.addAttribute("filterPropertyId", filterPropertyId);
        model.addAttribute("filterRoomId", filterRoomId);
        model.addAttribute("filterMonthValue", filterMonth != null ? filterMonth.toString() : "");
        model.addAttribute("filterFromDate", fromDate);
        model.addAttribute("filterToDate", toDate);
        model.addAttribute("filterError", filterError);
        model.addAttribute("datePriorityWarning", datePriorityWarning);
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("allCount", invoiceRepository.countByOwnerId(ownerId));
        model.addAttribute("paid", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PAID));
        model.addAttribute("pending", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING));
        model.addAttribute("overdue", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE));
        model.addAttribute("cancelled", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.CANCELLED));
        model.addAttribute("paidCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PAID));
        model.addAttribute("pendingCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PENDING));
        model.addAttribute("overdueCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE));
        model.addAttribute("cancelledCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.CANCELLED));
        return "admin/invoices";
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }

    private static String cleanSearch(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.isBlank()
                || "q".equalsIgnoreCase(clean)
                || "filterQ".equalsIgnoreCase(clean)) {
            return "";
        }
        return clean;
    }

    private static Invoice.InvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Invoice.InvoiceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return null;
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Map<UUID, Room> loadRooms(List<Invoice> invoices, Map<UUID, Room> ownerRoomById) {
        List<UUID> roomIds = invoices.stream()
                .map(Invoice::getRoomId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roomIds.isEmpty()) return Map.of();

        Map<UUID, Room> result = new HashMap<>();
        List<UUID> missingIds = roomIds.stream()
                .filter(id -> {
                    Room room = ownerRoomById.get(id);
                    if (room != null) {
                        result.put(id, room);
                        return false;
                    }
                    return true;
                })
                .toList();
        if (!missingIds.isEmpty()) {
            roomRepository.findAllById(missingIds).forEach(room -> result.put(room.getId(), room));
        }
        return result;
    }

    private Map<UUID, User> loadTenants(List<Invoice> invoices) {
        List<UUID> tenantIds = invoices.stream()
                .map(Invoice::getTenantUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tenantIds.isEmpty()) return Map.of();
        return userRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
