package vn.glassliving.room.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.review.repository.ReviewRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.entity.RoomImage;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class RoomWebController {

    private final RoomService roomService;
    private final ReviewRepository reviewRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final RoomImageRepository roomImageRepository;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String district,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) BigDecimal minPrice,
                       @RequestParam(required = false) BigDecimal maxPrice,
                       @RequestParam(required = false) Boolean petAllowed,
                       Model model) {
        Page<Room> rooms = roomService.search(district, type, minPrice, maxPrice, petAllowed, 0, 12);
        model.addAttribute("rooms", rooms.getContent());
        model.addAttribute("totalRooms", rooms.getTotalElements());
        model.addAttribute("district", district);
        model.addAttribute("type", type);
        model.addAttribute("petAllowed", Boolean.TRUE.equals(petAllowed));
        model.addAttribute("districts", List.of(
                Map.of("name", "Quận 1",   "count", 124),
                Map.of("name", "Quận 3",   "count", 88),
                Map.of("name", "Quận 7",   "count", 52),
                Map.of("name", "Thủ Đức",  "count", 41)
        ));
        return "customer/home";
    }

    @GetMapping("/rooms/{slug}")
    public String roomDetail(@PathVariable String slug, Model model) {
        Room room = roomService.getBySlug(slug);
        roomService.incrementViewCount(room.getId());

        Property property = propertyRepository.findById(room.getPropertyId()).orElse(null);
        User owner = userRepository.findById(room.getOwnerId()).orElse(null);

        // Build full address: prefer room.addressLine, else property.addressLine + district + city.
        String streetPart = (room.getAddressLine() != null && !room.getAddressLine().isBlank())
                ? room.getAddressLine()
                : (property != null ? property.getAddressLine() : "");
        String fullAddress = streetPart;
        if (room.getDistrict() != null) fullAddress += ", " + room.getDistrict();
        if (room.getCity() != null) fullAddress += ", " + room.getCity();
        fullAddress = fullAddress.replaceAll("^,\\s*", "");

        String addressEncoded = URLEncoder.encode(fullAddress, StandardCharsets.UTF_8);
        String mapEmbedUrl  = "https://maps.google.com/maps?q=" + addressEncoded + "&t=&z=15&ie=UTF8&iwloc=&output=embed";
        String mapSearchUrl = "https://www.google.com/maps/search/?api=1&query=" + addressEncoded;

        List<RoomImage> images = roomImageRepository.findByRoomIdOrderBySortOrderAsc(room.getId());

        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("owner", owner);
        model.addAttribute("images", images);
        model.addAttribute("fullAddress", fullAddress);
        model.addAttribute("mapEmbedUrl", mapEmbedUrl);
        model.addAttribute("mapSearchUrl", mapSearchUrl);
        model.addAttribute("similar", roomService.findSimilar(room, 3));
        model.addAttribute("reviews", reviewRepository.findByRoomIdOrderByCreatedAtDesc(
                room.getId(), PageRequest.of(0, 5)).getContent());
        return "customer/room-detail";
    }
}
