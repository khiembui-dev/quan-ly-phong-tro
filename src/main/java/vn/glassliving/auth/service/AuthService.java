package vn.glassliving.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.dto.JwtResponse;
import vn.glassliving.auth.dto.LoginRequest;
import vn.glassliving.auth.dto.RegisterRequest;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.auth.security.JwtService;
import vn.glassliving.common.exception.BusinessException;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw BusinessException.conflict("Email đã được sử dụng.");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw BusinessException.conflict("Số điện thoại đã được sử dụng.");
        }
        User.Role role = req.role();
        if (role == User.Role.ADMIN) {
            throw BusinessException.forbidden("Không thể đăng ký vai trò ADMIN qua form công khai.");
        }
        User u = User.builder()
                .email(req.email().trim().toLowerCase())
                .phone(req.phone())
                .fullName(req.fullName().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .roles(Set.of(role))
                .status(User.UserStatus.ACTIVE)   // TODO: set to PENDING_VERIFICATION when email OTP wired
                .emailVerified(false)
                .build();
        return userRepository.save(u);
    }

    @Transactional(readOnly = true)
    public JwtResponse loginIssueJwt(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
            AppUserDetails principal = (AppUserDetails) auth.getPrincipal();
            List<String> roles = principal.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .toList();
            String access = jwtService.generateAccess(principal.getId(), principal.getEmail(), roles);
            String refresh = jwtService.generateRefresh(principal.getId());
            return new JwtResponse(access, refresh, jwtService.getAccessTtlSeconds(),
                    principal.getId(), principal.getEmail(), principal.getFullName(), roles);
        } catch (BadCredentialsException ex) {
            throw BusinessException.unauthorized("Email hoặc mật khẩu không đúng.");
        }
    }
}
