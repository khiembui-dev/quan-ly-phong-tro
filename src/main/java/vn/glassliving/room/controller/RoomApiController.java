package vn.glassliving.room.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.common.dto.ApiResponse;
import vn.glassliving.common.dto.PageResponse;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomApiController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> list(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean petAllowed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Room> p = roomService.search(district, type, minPrice, maxPrice, petAllowed, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(p, RoomApiController::summary)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bySlug(@PathVariable String slug) {
        Room r = roomService.getBySlug(slug);
        Map<String, Object> data = new LinkedHashMap<>(summary(r));
        data.put("description", r.getDescription());
        data.put("amenities", r.getAmenities());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private static Map<String, Object> summary(Room r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("slug", r.getSlug());
        m.put("title", r.getTitle());
        m.put("type", r.getType().name());
        m.put("status", r.getStatus().name());
        m.put("district", r.getDistrict());
        m.put("city", r.getCity());
        m.put("areaSqm", r.getAreaSqm());
        m.put("priceMonthly", r.getPriceMonthly());
        m.put("priceFormatted", MoneyFormatter.vnd(r.getPriceMonthly()));
        m.put("ratingAvg", r.getRatingAvg());
        m.put("ratingCount", r.getRatingCount());
        m.put("petAllowed", r.isPetAllowed());
        m.put("hasBalcony", r.isHasBalcony());
        m.put("coverUrl", r.getCoverUrl());
        return m;
    }
}
