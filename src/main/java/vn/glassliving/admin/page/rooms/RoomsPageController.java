package vn.glassliving.admin.page.rooms;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.room.service.RoomAdminService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomsPageController {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RoomAdminService roomAdminService;
    private final AmenityRepository amenityRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String rooms(@AuthenticationPrincipal AppUserDetails me,
                        @RequestParam(required = false) UUID propertyId,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String type,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) UUID edit,
                        @RequestParam(required = false) UUID clone,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "12") int size,
                        Model model) {
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(me.getId());
        Property current = null;
        if (propertyId != null) {
            current = properties.stream().filter(p -> p.getId().equals(propertyId)).findFirst().orElse(null);
        }

        int pageSafe = Math.max(0, page);
        int sizeSafe = Math.max(1, Math.min(48, size));
        Pageable pageable = PageRequest.of(pageSafe, sizeSafe, Sort.by(Sort.Direction.ASC, "code"));

        Page<Room> roomPage;
        if (propertyId != null) {
            List<Room> rooms = applyFilters(roomRepository.findByPropertyIdOrderByCodeAsc(propertyId), status, type, q);
            roomPage = pageRooms(rooms, pageable, pageSafe, sizeSafe);
        } else {
            List<Room> rooms = applyFilters(
                    roomRepository.findByOwnerId(me.getId(), PageRequest.of(0, 1000)).getContent(),
                    status, type, q);
            roomPage = pageRooms(rooms, pageable, pageSafe, sizeSafe);
        }

        long allCount = roomRepository.findByOwnerId(me.getId(), PageRequest.of(0, 1)).getTotalElements();
        long availableCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.AVAILABLE);
        long occupiedCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.OCCUPIED);
        long maintCount = roomRepository.countByOwnerIdAndStatus(me.getId(), Room.RoomStatus.MAINTENANCE);

        model.addAttribute("activeNav", "rooms");
        model.addAttribute("pageTitle", "Phòng & Cơ sở");
        model.addAttribute("properties", properties);
        model.addAttribute("currentProperty", current);
        model.addAttribute("roomPage", roomPage);
        model.addAttribute("rooms", roomPage.getContent());
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterQ", q);
        model.addAttribute("countAll", allCount);
        model.addAttribute("countAvailable", availableCount);
        model.addAttribute("countOccupied", occupiedCount);
        model.addAttribute("countMaintenance", maintCount);

        RoomForm prefilledForm = null;
        if (edit != null) {
            prefilledForm = roomAdminService.getEditForm(me.getId(), edit);
            model.addAttribute("formMode", "edit");
            model.addAttribute("formAction", prefilledForm != null ? "/admin/rooms/" + edit + "/update" : "/admin/rooms");
        } else if (clone != null) {
            prefilledForm = roomAdminService.getCloneForm(me.getId(), clone);
            model.addAttribute("formMode", "clone");
            model.addAttribute("formAction", "/admin/rooms");
        } else {
            model.addAttribute("formMode", "create");
            model.addAttribute("formAction", "/admin/rooms");
        }
        model.addAttribute("editForm", prefilledForm);

        addEditJson(model, prefilledForm);
        addTenantData(model, roomPage);
        addAmenityGroups(model);

        return "admin/rooms";
    }

    private static Page<Room> pageRooms(List<Room> rooms, Pageable pageable, int page, int size) {
        int from = Math.min(page * size, rooms.size());
        int to = Math.min(from + size, rooms.size());
        return new PageImpl<>(rooms.subList(from, to), pageable, rooms.size());
    }

    private static List<Room> applyFilters(List<Room> rooms, String status, String type, String q) {
        return rooms.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equals(status))
                .filter(r -> type == null || type.isBlank() || r.getType().name().equals(type))
                .filter(r -> q == null || q.isBlank()
                        || r.getCode().toLowerCase().contains(q.toLowerCase())
                        || r.getTitle().toLowerCase().contains(q.toLowerCase()))
                .toList();
    }

    private static void addEditJson(Model model, RoomForm prefilledForm) {
        if (prefilledForm == null) {
            model.addAttribute("editFeesJson", new ArrayList<>());
            model.addAttribute("editAmenityCodesJson", new ArrayList<>());
            model.addAttribute("editCustomAmenitiesJson", new ArrayList<>());
            return;
        }

        List<Map<String, Object>> feePairs = new ArrayList<>();
        int feeCount = Math.min(
                prefilledForm.getExtraFeeNames() != null ? prefilledForm.getExtraFeeNames().size() : 0,
                prefilledForm.getExtraFeeAmounts() != null ? prefilledForm.getExtraFeeAmounts().size() : 0);
        for (int i = 0; i < feeCount; i++) {
            Map<String, Object> pair = new HashMap<>();
            pair.put("name", prefilledForm.getExtraFeeNames().get(i));
            pair.put("amount", prefilledForm.getExtraFeeAmounts().get(i));
            feePairs.add(pair);
        }
        model.addAttribute("editFeesJson", feePairs);
        model.addAttribute("editAmenityCodesJson", prefilledForm.getAmenityCodes());

        List<Map<String, Object>> customPairs = new ArrayList<>();
        int customCount = Math.min(
                prefilledForm.getCustomAmenityNames() != null ? prefilledForm.getCustomAmenityNames().size() : 0,
                prefilledForm.getCustomAmenityCategories() != null ? prefilledForm.getCustomAmenityCategories().size() : 0);
        for (int i = 0; i < customCount; i++) {
            Map<String, Object> pair = new HashMap<>();
            pair.put("name", prefilledForm.getCustomAmenityNames().get(i));
            pair.put("category", prefilledForm.getCustomAmenityCategories().get(i));
            customPairs.add(pair);
        }
        model.addAttribute("editCustomAmenitiesJson", customPairs);
    }

    private void addTenantData(Model model, Page<Room> roomPage) {
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
}
