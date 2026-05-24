package vn.glassliving.admin.page.properties;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
public class PropertiesPageController {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public String properties(@AuthenticationPrincipal AppUserDetails me, Model model) {
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(me.getId());
        Map<UUID, Long> roomCountByProp = new HashMap<>();
        for (Property property : properties) {
            roomCountByProp.put(property.getId(), (long) roomRepository.findByPropertyIdOrderByCodeAsc(property.getId()).size());
        }

        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", "Cơ sở & Đơn giá");
        model.addAttribute("properties", properties);
        model.addAttribute("roomCountByProp", roomCountByProp);
        return "admin/properties";
    }
}
