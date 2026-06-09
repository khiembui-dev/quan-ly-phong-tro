package vn.glassliving.admin.page.rooms;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.room.service.RoomAdminService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomsPageController {

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final AmenityRepository amenityRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final RoomAdminService roomAdminService;
    private final MessageSource messageSource;

    @GetMapping
    public String rooms(@AuthenticationPrincipal AppUserDetails me,
                        @RequestParam(required = false) UUID propertyId,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String type,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Locale locale,
                        Model model) {
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(me.getId());
        Map<UUID, String> propertyNameById = new HashMap<>();
        for (Property property : properties) {
            propertyNameById.put(property.getId(), property.getName());
        }

        Property current = null;
        invoiceService.refreshAgingStatuses(me.getId());
        if (propertyId != null) {
            current = properties.stream().filter(p -> p.getId().equals(propertyId)).findFirst().orElse(null);
        }
        String query = cleanSearch(q);

        int sizeSafe = normalizePageSize(size);
        int pageSafe = Math.max(0, page);
        Pageable pageable = PageRequest.of(pageSafe, sizeSafe, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Room> sourceRooms = propertyId != null
                ? roomRepository.findByPropertyIdOrderByCodeAsc(propertyId)
                : roomRepository.findByOwnerId(me.getId(), PageRequest.of(0, 5000, Sort.by(Sort.Direction.ASC, "code"))).getContent();
        Page<Room> roomPage = pageRooms(sortNewestFirst(applyFilters(sourceRooms, status, type, query)), pageable, pageSafe, sizeSafe);

        long allCount = roomRepository.findByOwnerId(me.getId(), PageRequest.of(0, 1)).getTotalElements();
        long availableCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.AVAILABLE);
        long occupiedCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.OCCUPIED);
        long maintCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.MAINTENANCE);

        model.addAttribute("activeNav", "rooms");
        model.addAttribute("pageTitle", msg(locale, "rooms.title"));
        model.addAttribute("properties", properties);
        model.addAttribute("propertyNameById", propertyNameById);
        model.addAttribute("currentProperty", current);
        model.addAttribute("roomPage", roomPage);
        model.addAttribute("rooms", roomPage.getContent());
        model.addAttribute("currentPage", roomPage.getNumber());
        model.addAttribute("totalPages", roomPage.getTotalPages());
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterQ", query);
        model.addAttribute("countAll", allCount);
        model.addAttribute("countAvailable", availableCount);
        model.addAttribute("countOccupied", occupiedCount);
        model.addAttribute("countMaintenance", maintCount);
        addTenantData(model, roomPage);
        model.addAttribute("lateInvoiceByRoomId", lateInvoiceByRoomId(me.getId(), roomPage.getContent()));
        return "admin/rooms";
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
    public String detail(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         Locale locale,
                         Model model) {
        Optional<Room> maybeRoom = roomRepository.findById(id).filter(r -> r.getOwnerId().equals(me.getId()));
        if (maybeRoom.isEmpty()) {
            return "redirect:/admin/rooms";
        }
        Room room = maybeRoom.get();
        invoiceService.refreshAgingStatuses(me.getId());
        Property property = propertyRepository.findById(room.getPropertyId()).orElse(null);
        User tenant = room.getCurrentTenantId() != null
                ? userRepository.findById(room.getCurrentTenantId()).orElse(null)
                : null;
        Contract activeContract = tenant != null
                ? contractRepository.findFirstByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByStartDateDesc(
                        me.getId(), room.getId(), tenant.getId(), Contract.ContractStatus.ACTIVE).orElse(null)
                : null;
        var invoices = invoiceRepository.findTop18ByOwnerIdAndRoomIdOrderByPeriodYearDescPeriodMonthDescCreatedAtDesc(
                me.getId(), room.getId());

        model.addAttribute("activeNav", "rooms");
        model.addAttribute("pageTitle", room.getTitle());
        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("tenant", tenant);
        model.addAttribute("activeContract", activeContract);
        model.addAttribute("latestInvoice", invoices.isEmpty() ? null : invoices.get(0));
        model.addAttribute("lateInvoice",
                invoiceRepository.findFirstByOwnerIdAndRoomIdAndStatusInAndDueDateBeforeOrderByDueDateAsc(
                        me.getId(), room.getId(), lateInvoiceStatuses(), LocalDate.now()).orElse(null));
        model.addAttribute("roomImages", roomImageRepository.findByRoomIdOrderBySortOrderAsc(room.getId()));
        model.addAttribute("roomFixedFees", resolveRoomFixedFees(room, property));
        return "admin/room-detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserDetails me,
                       @PathVariable UUID id,
                       Locale locale,
                       Model model) {
        Room room = roomRepository.findById(id)
                .filter(r -> r.getOwnerId().equals(me.getId()))
                .orElse(null);
        if (room == null) {
            return "redirect:/admin/rooms";
        }
        if (!model.containsAttribute("form")) {
            RoomForm form = roomAdminService.getEditForm(me.getId(), id);
            if (form != null && isBlank(form.getAddressLine())) {
                Property property = propertyRepository.findById(room.getPropertyId()).orElse(null);
                form.setAddressLine(fallbackAddress(room, property));
            }
            model.addAttribute("form", form != null ? form : new RoomForm());
        }
        String editTitle = msg(locale, "common.edit") + " " + msg(locale, "rooms.title").toLowerCase(locale);
        prepareRoomFormModel(model, me, editTitle);
        model.addAttribute("roomFormMode", "edit");
        model.addAttribute("roomFormAction", "/admin/rooms/" + id + "/edit");
        model.addAttribute("roomFormBackUrl", "/admin/rooms/" + id);
        model.addAttribute("roomFormHeading", editTitle);
        model.addAttribute("roomFormDescription", msg(locale, "rooms.form.editDesc"));
        model.addAttribute("roomFormSubmitLabel", msg(locale, "rooms.form.saveChanges"));
        model.addAttribute("roomFormSubmitLoading", msg(locale, "rooms.form.saving"));
        model.addAttribute("editRoom", room);
        model.addAttribute("roomImages", roomImageRepository.findByRoomIdOrderBySortOrderAsc(room.getId()));
        return "admin/room-create";
    }

    @GetMapping("/create")
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @RequestParam(required = false) UUID propertyId,
                         Locale locale,
                         Model model) {
        if (!model.containsAttribute("form")) {
            RoomForm form = new RoomForm();
            if (propertyId != null) {
                form.setPropertyId(propertyId.toString());
            }
            model.addAttribute("form", form);
        }
        String createTitle = msg(locale, "rooms.add");
        prepareRoomFormModel(model, me, createTitle);
        model.addAttribute("roomFormMode", "create");
        model.addAttribute("roomFormAction", "/admin/rooms/create");
        model.addAttribute("roomFormBackUrl", "/admin/rooms");
        model.addAttribute("roomFormHeading", createTitle);
        model.addAttribute("roomFormDescription", msg(locale, "rooms.form.createDesc"));
        model.addAttribute("roomFormSubmitLabel", msg(locale, "rooms.form.createRoom"));
        model.addAttribute("roomFormSubmitLoading", msg(locale, "rooms.form.creating"));
        return "admin/room-create";
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }

    private static Page<Room> pageRooms(List<Room> rooms, Pageable pageable, int page, int size) {
        int from = Math.min(page * size, rooms.size());
        int to = Math.min(from + size, rooms.size());
        return new PageImpl<>(rooms.subList(from, to), pageable, rooms.size());
    }

    private static List<Room> applyFilters(List<Room> rooms, String status, String type, String q) {
        String query = q == null ? "" : q.trim().toLowerCase();
        return rooms.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equals(status))
                .filter(r -> type == null || type.isBlank() || r.getType().name().equals(type))
                .filter(r -> query.isBlank()
                        || safe(r.getCode()).toLowerCase().contains(query)
                        || safe(r.getTitle()).toLowerCase().contains(query)
                        || safe(r.getAddressLine()).toLowerCase().contains(query))
                .toList();
    }

    private static List<Room> sortNewestFirst(List<Room> rooms) {
        return rooms.stream()
                .sorted(Comparator
                        .comparing(Room::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    private void prepareRoomFormModel(Model model, AppUserDetails me, String title) {
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(me.getId());
        model.addAttribute("activeNav", "rooms");
        model.addAttribute("pageTitle", title);
        model.addAttribute("properties", properties);
        model.addAttribute("propertyTariffs", buildPropertyTariffs(properties));
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        addTenantCatalog(model);
        addAmenityGroups(model);
    }

    private void addTenantData(Model model, Page<Room> roomPage) {
        addTenantCatalog(model);

        Map<UUID, User> tenantByRoomId = new HashMap<>();
        List<UUID> activeTenantIds = roomPage.getContent().stream()
                .map(Room::getCurrentTenantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!activeTenantIds.isEmpty()) {
            Map<UUID, User> usersById = new HashMap<>();
            userRepository.findAllById(activeTenantIds).forEach(u -> usersById.put(u.getId(), u));
            for (Room room : roomPage.getContent()) {
                UUID tenantId = room.getCurrentTenantId();
                if (tenantId != null) {
                    User tenant = usersById.get(tenantId);
                    if (tenant != null) {
                        tenantByRoomId.put(room.getId(), tenant);
                    }
                }
            }
        }
        model.addAttribute("tenantByRoomId", tenantByRoomId);
    }

    private void addTenantCatalog(Model model) {
        var tenants = userRepository.findByRolesContaining(User.Role.TENANT);
        model.addAttribute("tenantUsers", tenants);

        List<Map<String, Object>> tenantsLite = new ArrayList<>(tenants.size());
        for (User tenant : tenants) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", tenant.getId().toString());
            m.put("fullName", tenant.getFullName() != null ? tenant.getFullName() : "");
            m.put("email", tenant.getEmail() != null ? tenant.getEmail() : "");
            m.put("phone", tenant.getPhone() != null ? tenant.getPhone() : "");
            m.put("avatarUrl", tenant.getAvatarUrl());
            m.put("status", tenant.getStatus() != null ? tenant.getStatus().name() : "");
            tenantsLite.add(m);
        }
        model.addAttribute("tenantsJson", tenantsLite);
    }

    private Map<UUID, Invoice> lateInvoiceByRoomId(UUID ownerId, List<Room> rooms) {
        List<UUID> roomIds = rooms.stream().map(Room::getId).distinct().toList();
        if (roomIds.isEmpty()) return Map.of();
        Map<UUID, Invoice> result = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findByOwnerIdAndRoomIdInAndStatusInAndDueDateBeforeOrderByDueDateAsc(
                ownerId, roomIds, lateInvoiceStatuses(), LocalDate.now())) {
            result.putIfAbsent(invoice.getRoomId(), invoice);
        }
        return result;
    }

    private static List<Invoice.InvoiceStatus> lateInvoiceStatuses() {
        return List.of(
                Invoice.InvoiceStatus.PENDING,
                Invoice.InvoiceStatus.PARTIALLY_PAID,
                Invoice.InvoiceStatus.OVERDUE,
                Invoice.InvoiceStatus.CANCELLED
        );
    }

    private void addAmenityGroups(Model model) {
        Map<String, List<Amenity>> amenityGroups = new LinkedHashMap<>();
        amenityGroups.put("FURNITURE", new ArrayList<>());
        amenityGroups.put("UTILITY", new ArrayList<>());
        amenityGroups.put("RULE", new ArrayList<>());
        for (Amenity amenity : amenityRepository.findAllByOrderBySortOrderAsc()) {
            String key = amenity.getCategory() != null ? amenity.getCategory().name() : "OTHER";
            amenityGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(amenity);
        }
        model.addAttribute("amenityGroups", amenityGroups);
    }

    private static Map<String, Object> buildPropertyTariffs(List<Property> properties) {
        Map<String, Object> tariffs = new LinkedHashMap<>();
        for (Property property : properties) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", property.getId().toString());
            item.put("name", property.getName());
            item.put("address", fallbackPropertyAddress(property));
            item.put("electricUnit", moneyValue(property.getElectricUnit()));
            item.put("waterUnit", moneyValue(property.getWaterUnit()));
            item.put("serviceFee", moneyValue(property.getServiceFeeDefault()));
            item.put("fees", propertyFixedFees(property));
            tariffs.put(property.getId().toString(), item);
        }
        return tariffs;
    }

    private static List<Property.ExtraFee> resolveRoomFixedFees(Room room, Property property) {
        if (room.isInheritTariff() && property != null) {
            return propertyFixedFees(property).stream()
                    .map(item -> new Property.ExtraFee(
                            String.valueOf(item.get("name")),
                            new java.math.BigDecimal(String.valueOf(item.get("amount")))))
                    .toList();
        }
        return room.getExtraFees() != null ? room.getExtraFees() : List.of();
    }

    private static List<Map<String, Object>> propertyFixedFees(Property property) {
        List<Map<String, Object>> fees = new ArrayList<>();
        addTariffFee(fees, "Internet", property.getInternetFee());
        addTariffFee(fees, "Rác", property.getGarbageFee());
        addTariffFee(fees, "Quản lý", property.getManagementFee());
        if (property.getExtraFees() != null) {
            for (Property.ExtraFee fee : property.getExtraFees()) {
                if (fee == null || isBlank(fee.getName())) continue;
                addTariffFee(fees, fee.getName().trim(), fee.getAmount());
            }
        }
        return fees;
    }

    private static void addTariffFee(List<Map<String, Object>> fees, String name, java.math.BigDecimal amount) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("name", name);
        fee.put("amount", moneyValue(amount));
        fees.add(fee);
    }

    private static String moneyValue(java.math.BigDecimal amount) {
        return amount != null ? amount.stripTrailingZeros().toPlainString() : "0";
    }

    private static String fallbackPropertyAddress(Property property) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(property.getAddressLine())) parts.add(property.getAddressLine().trim());
        if (!isBlank(property.getDistrict())) parts.add(property.getDistrict().trim());
        if (!isBlank(property.getCity())) parts.add(property.getCity().trim());
        return String.join(", ", parts);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String fallbackAddress(Room room, Property property) {
        if (property != null && !isBlank(property.getAddressLine())) {
            return property.getAddressLine() + ", " + property.getDistrict() + ", " + property.getCity();
        }
        return safe(room.getDistrict()) + (isBlank(room.getCity()) ? "" : ", " + room.getCity());
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
