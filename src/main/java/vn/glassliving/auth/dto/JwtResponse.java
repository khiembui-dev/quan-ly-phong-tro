package vn.glassliving.auth.dto;

import java.util.List;
import java.util.UUID;

public record JwtResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        String email,
        String fullName,
        List<String> roles
) {}
