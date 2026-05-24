package vn.glassliving.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.booking.dto.CreateBookingRequest;
import vn.glassliving.booking.entity.Booking;
import vn.glassliving.booking.service.BookingService;
import vn.glassliving.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingApiController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateBookingRequest req,
                                                                   @AuthenticationPrincipal AppUserDetails me) {
        Booking b = bookingService.create(me.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "bookingId", b.getId(),
                "code", b.getCode(),
                "status", b.getStatus().name(),
                "totalDue", b.getTotalDue()
        ), "Đã ghi nhận yêu cầu đặt phòng."));
    }
}
