package vn.glassliving.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);
    private final AdminBootstrapSeeder seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder, environment);

    @Test
    void createsFirstActiveAdminFromEnvironment() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("Admin@Example.com")).thenReturn(false);
        when(environment.getProperty("ADMIN_EMAIL")).thenReturn(" Admin@Example.com ");
        when(environment.getProperty("ADMIN_PASSWORD")).thenReturn("secret123");
        when(environment.getProperty("ADMIN_FULL_NAME")).thenReturn(" SmartRent Admin ");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getFullName()).isEqualTo("SmartRent Admin");
        assertThat(admin.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(admin.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(admin.getRoles()).containsExactly(User.Role.ADMIN);
        assertThat(admin.isEmailVerified()).isTrue();
    }

    @Test
    void skipsWhenAdminAlreadyExists() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(true);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsFastInProductionWhenEnvironmentIsMissing() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL");

        verify(userRepository, never()).save(any());
    }
}
