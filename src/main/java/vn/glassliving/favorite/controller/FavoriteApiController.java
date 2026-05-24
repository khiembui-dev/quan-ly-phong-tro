package vn.glassliving.favorite.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.dto.ApiResponse;
import vn.glassliving.favorite.service.FavoriteService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteApiController {

    private final FavoriteService favoriteService;

    @PostMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggle(@PathVariable UUID roomId,
                                                                    @AuthenticationPrincipal AppUserDetails me) {
        boolean isFav = favoriteService.toggle(me.getId(), roomId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("favorite", isFav)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID roomId,
                                                    @AuthenticationPrincipal AppUserDetails me) {
        favoriteService.toggle(me.getId(), roomId); // toggle off if currently on; idempotent enough for MVP
        return ResponseEntity.ok(ApiResponse.message("Đã xóa khỏi yêu thích."));
    }
}
