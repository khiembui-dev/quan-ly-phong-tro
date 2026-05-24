package vn.glassliving.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.auth.dto.JwtResponse;
import vn.glassliving.auth.dto.LoginRequest;
import vn.glassliving.auth.dto.RegisterRequest;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.auth.service.AuthService;
import vn.glassliving.common.dto.ApiResponse;

import java.util.Map;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest req) {
        User u = authService.register(req);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "userId", u.getId(),
                "email", u.getEmail()
        ), "Đăng ký thành công. Vui lòng kiểm tra email để xác thực."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.loginIssueJwt(req)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(@AuthenticationPrincipal AppUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("Yêu cầu đăng nhập."));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "id", principal.getId(),
                "email", principal.getEmail(),
                "fullName", principal.getFullName(),
                "roles", principal.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList()
        )));
    }
}
