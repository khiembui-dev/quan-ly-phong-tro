package vn.glassliving.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.glassliving.auth.entity.User;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Pattern(regexp = "^(0|\\+84)[3-9]\\d{8}$", message = "Số điện thoại không hợp lệ") String phone,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank User.Role role,
        boolean acceptTerms
) {}
