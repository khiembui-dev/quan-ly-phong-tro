package vn.glassliving.room.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.entity.RoomImage;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RoomWebController {

    private final RoomService roomService;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final RoomImageRepository roomImageRepository;
    private final AutomationSettingRepository automationSettingRepository;

    @Value("${app.customer-support.phone:}")
    private String supportPhone;

    @Value("${app.customer-support.email:hotro@smartrent.vn}")
    private String supportEmail;

    @Value("${app.customer-support.zalo:}")
    private String supportZalo;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String district,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) BigDecimal maxPrice,
                       @RequestParam(required = false) Boolean petAllowed,
                       Model model) {
        var roomPage = roomService.search(null, null, null, null, null, 0, 8);
        model.addAttribute("rooms", roomPage.getContent());
        model.addAttribute("totalRooms", roomPage.getTotalElements());
        model.addAttribute("district", null);
        model.addAttribute("type", null);
        model.addAttribute("maxPrice", null);
        model.addAttribute("petAllowed", null);
        return "customer/home";
    }

    @GetMapping({"/rooms/{slug}", "/customer/rooms/{slug}", "/customer/room-detail/{slug}"})
    public String roomDetail(@PathVariable String slug, Model model) {
        Room room = roomService.getBySlug(slug);
        roomService.incrementViewCount(room.getId());

        Property property = propertyRepository.findById(room.getPropertyId()).orElse(null);
        User owner = userRepository.findById(room.getOwnerId()).orElse(null);
        AutomationSetting contactSetting = automationSettingRepository.findByOwnerId(room.getOwnerId()).orElse(null);
        AutomationSetting fallbackSetting = contactSetting == null
                ? automationSettingRepository.findFirstByContactEmailIsNotNullOrContactZaloIsNotNullOrderByUpdatedAtDesc().orElse(null)
                : null;
        String configuredPhoneOrZalo = firstNonBlank(
                contactSetting != null ? contactSetting.getContactZalo() : null,
                fallbackSetting != null ? fallbackSetting.getContactZalo() : null,
                supportZalo,
                supportPhone);
        String contactPhone = configuredPhoneOrZalo;
        String contactZalo = firstNonBlank(
                contactSetting != null ? contactSetting.getContactZalo() : null,
                fallbackSetting != null ? fallbackSetting.getContactZalo() : null,
                supportZalo,
                contactPhone);
        String contactEmail = firstNonBlank(
                contactSetting != null ? contactSetting.getContactEmail() : null,
                fallbackSetting != null ? fallbackSetting.getContactEmail() : null,
                supportEmail,
                owner != null ? owner.getEmail() : null);

        String streetPart = (room.getAddressLine() != null && !room.getAddressLine().isBlank())
                ? room.getAddressLine()
                : (property != null ? property.getAddressLine() : "");
        String fullAddress = streetPart;
        if (room.getDistrict() != null) fullAddress += ", " + room.getDistrict();
        if (room.getCity() != null) fullAddress += ", " + room.getCity();
        fullAddress = fullAddress.replaceAll("^,\\s*", "");

        String addressEncoded = URLEncoder.encode(fullAddress, StandardCharsets.UTF_8);
        String mapEmbedUrl = "https://maps.google.com/maps?q=" + addressEncoded + "&t=&z=15&ie=UTF8&iwloc=&output=embed";
        String mapSearchUrl = "https://www.google.com/maps/search/?api=1&query=" + addressEncoded;

        List<RoomImage> images = roomImageRepository.findByRoomIdOrderBySortOrderAsc(room.getId());

        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("owner", owner);
        model.addAttribute("images", images);
        model.addAttribute("fullAddress", fullAddress);
        model.addAttribute("mapEmbedUrl", mapEmbedUrl);
        model.addAttribute("mapSearchUrl", mapSearchUrl);
        model.addAttribute("contactPhone", contactPhone);
        model.addAttribute("contactPhoneUrl", telUrl(contactPhone));
        model.addAttribute("contactZaloUrl", zaloUrl(contactZalo));
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("similar", roomService.findSimilar(room, 3));
        return "customer/room-detail";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String telUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : "tel:" + normalized;
    }

    private static String zaloUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : "https://zalo.me/" + digits;
    }
}
