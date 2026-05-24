package vn.glassliving.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.dto.ApiResponse;
import vn.glassliving.notification.service.NotificationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> latest(@AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.latest(me.getId())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(ApiResponse.message("OK"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal AppUserDetails me) {
        notificationService.markAllRead(me.getId());
        return ResponseEntity.ok(ApiResponse.message("Đã đánh dấu đã đọc."));
    }
}
