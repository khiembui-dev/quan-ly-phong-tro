package vn.glassliving.admin.page.properties;

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
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.property.dto.PropertyForm;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.property.service.PropertyService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
public class PropertiesPageController {

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final PropertyService propertyService;
    private final MessageSource messageSource;

    @GetMapping
    public String properties(@AuthenticationPrincipal AppUserDetails me,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Locale locale,
                             Model model) {
        int currentPage = Math.max(page, 0);
        int sizeSafe = normalizePageSize(size);
        var pageable = PageRequest.of(currentPage, sizeSafe, Sort.by(Sort.Direction.DESC, "createdAt"));
        var propertyPage = propertyRepository.findByOwnerId(me.getId(), pageable);
        if (propertyPage.isEmpty() && currentPage > 0 && propertyPage.getTotalPages() > 0) {
            return "redirect:/admin/properties?page=" + (propertyPage.getTotalPages() - 1) + "&size=" + sizeSafe;
        }
        var properties = propertyPage.getContent();

        Map<UUID, Long> roomCountByProp = new HashMap<>();
        Map<UUID, Long> availableRoomCountByProp = new HashMap<>();
        for (Property property : properties) {
            var rooms = roomRepository.findByPropertyIdOrderByCodeAsc(property.getId());
            roomCountByProp.put(property.getId(), (long) rooms.size());
            availableRoomCountByProp.put(property.getId(), rooms.stream()
                    .filter(room -> room.getStatus() == Room.RoomStatus.AVAILABLE)
                    .count());
        }

        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", msg(locale, "properties.title"));
        model.addAttribute("properties", properties);
        model.addAttribute("propertyPage", propertyPage);
        model.addAttribute("currentPage", propertyPage.getNumber());
        model.addAttribute("totalPages", propertyPage.getTotalPages());
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("roomCountByProp", roomCountByProp);
        model.addAttribute("availableRoomCountByProp", availableRoomCountByProp);
        return "admin/properties";
    }

    @GetMapping("/create")
    public String create(Locale locale, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PropertyForm());
        }
        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", msg(locale, "properties.add"));
        return "admin/property-create";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserDetails me,
                       @PathVariable UUID id,
                       Locale locale,
                       Model model) {
        Property property = propertyRepository.findById(id)
                .filter(p -> p.getOwnerId().equals(me.getId()))
                .orElse(null);
        if (property == null) {
            return "redirect:/admin/properties";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", propertyService.toForm(property));
        }
        model.addAttribute("property", property);
        model.addAttribute("propertyId", id);
        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", msg(locale, "common.edit") + " " + msg(locale, "properties.title").toLowerCase(locale));
        return "admin/property-edit";
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }
}
