package vn.glassliving.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;

import java.util.Arrays;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapSeeder implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "ADMIN_EMAIL";
    private static final String ADMIN_PASSWORD = "ADMIN_PASSWORD";
    private static final String ADMIN_FULL_NAME = "ADMIN_FULL_NAME";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(User.Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an ADMIN user already exists.");
            return;
        }

        String email = clean(environment.getProperty(ADMIN_EMAIL));
        String password = clean(environment.getProperty(ADMIN_PASSWORD));
        String fullName = clean(environment.getProperty(ADMIN_FULL_NAME));

        if (email == null || password == null || fullName == null) {
            String message = "Admin bootstrap requires ADMIN_EMAIL, ADMIN_PASSWORD and ADMIN_FULL_NAME when no ADMIN user exists.";
            if (isProdProfile()) {
                log.error("{} Startup will stop because the active profile is production.", message);
                throw new IllegalStateException(message);
            }
            log.warn("{} Skipping bootstrap for non-production profile.", message);
            return;
        }

        validateAdminConfig(email, password);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            String message = "Admin bootstrap cannot create ADMIN because ADMIN_EMAIL already belongs to a non-admin user: " + email;
            if (isProdProfile()) {
                log.error(message);
                throw new IllegalStateException(message);
            }
            log.warn(message);
            return;
        }

        User admin = User.builder()
                .email(email.toLowerCase())
                .emailVerified(true)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(password))
                .roles(Set.of(User.Role.ADMIN))
                .status(User.UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
        log.info("Admin bootstrap created first ADMIN user from environment: {}", admin.getEmail());
    }

    private void validateAdminConfig(String email, String password) {
        if (!isValidEmail(email)) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid email address.");
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new IllegalStateException("ADMIN_PASSWORD must be 8 to 64 characters.");
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
    }

    private static boolean isValidEmail(String value) {
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return true;
        } catch (AddressException ex) {
            return false;
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
