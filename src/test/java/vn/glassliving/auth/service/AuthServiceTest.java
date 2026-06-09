package vn.glassliving.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.glassliving.auth.dto.RegisterRequest;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService authService = new AuthService(
            userRepository,
            passwordEncoder,
            null,
            null
    );

    @Test
    void publicRegistrationAlwaysCreatesActiveTenant() {
        when(userRepository.existsByEmailIgnoreCase("USER@Example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.register(new RegisterRequest(
                " Nguyen Van A ",
                "USER@Example.com",
                "0901234567",
                "secret123",
                true
        ));

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(user.getRoles()).containsExactly(User.Role.TENANT);
    }
}
