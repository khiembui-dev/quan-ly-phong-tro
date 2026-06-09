package vn.glassliving.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.glassliving.auth.entity.PasswordResetCode;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.PasswordResetCodeRepository;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.automation.service.AutomationEmailService;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.room.repository.RoomRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordResetCodeRepository codeRepository = mock(PasswordResetCodeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TestPasswordResetEmailService emailService = new TestPasswordResetEmailService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-08T10:00:00Z"), ZoneOffset.UTC);
    private final PasswordResetCodeGenerator codeGenerator = () -> "123456";
    private final PasswordResetService service = new PasswordResetService(
            userRepository,
            codeRepository,
            passwordEncoder,
            emailService,
            codeGenerator,
            clock
    );

    @Test
    void requestResetCodeStoresHashedSixDigitCodeAndSendsEmail() {
        User user = tenant();
        when(userRepository.findByEmailIgnoreCase("tenant@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("123456")).thenReturn("hashed-code");
        when(codeRepository.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestResetCode(" TENANT@Example.Test ");

        assertThat(emailService.toEmail).isEqualTo("tenant@example.test");
        assertThat(emailService.fullName).isEqualTo("Tenant A");
        assertThat(emailService.code).isEqualTo("123456");
        assertThat(emailService.ttlMinutes).isEqualTo(10);
        verify(codeRepository).save(org.mockito.ArgumentMatchers.argThat(code ->
                code.getUserId().equals(user.getId())
                        && code.getEmail().equals("tenant@example.test")
                        && code.getCodeHash().equals("hashed-code")
                        && code.getExpiresAt().equals(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusMinutes(10))
                        && code.getAttemptCount() == 0
        ));
    }

    @Test
    void requestResetCodeDoesNotRevealUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.test")).thenReturn(Optional.empty());

        service.requestResetCode("missing@example.test");

        assertThat(emailService.code).isNull();
        org.mockito.Mockito.verifyNoInteractions(codeRepository);
    }

    @Test
    void verifyCodeReturnsResetIdForLatestActiveCode() {
        PasswordResetCode resetCode = activeCode();
        when(codeRepository.findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant@example.test"),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.of(resetCode));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        PasswordResetService.VerifiedReset verified = service.verifyCode("tenant@example.test", "123456");

        assertThat(verified.resetCodeId()).isEqualTo(resetCode.getId());
        assertThat(verified.email()).isEqualTo("tenant@example.test");
        assertThat(resetCode.getAttemptCount()).isZero();
    }

    @Test
    void invalidCodeIncrementsAttemptCountAndFails() {
        PasswordResetCode resetCode = activeCode();
        when(codeRepository.findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant@example.test"),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.of(resetCode));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyCode("tenant@example.test", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mã xác nhận");

        assertThat(resetCode.getAttemptCount()).isEqualTo(1);
        assertThat(resetCode.getLastAttemptAt()).isNotNull();
        verify(codeRepository).save(resetCode);
    }

    @Test
    void resetPasswordConsumesCodeAndUpdatesPassword() {
        User user = tenant();
        PasswordResetCode resetCode = activeCode();
        when(codeRepository.findByIdAndEmailAndConsumedAtIsNull(resetCode.getId(), "tenant@example.test"))
                .thenReturn(Optional.of(resetCode));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-password-hash");

        service.resetPassword("tenant@example.test", resetCode.getId(), "newPassword123", "newPassword123");

        assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
        assertThat(resetCode.getConsumedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(codeRepository).save(resetCode);
    }

    @Test
    void resetPasswordRequiresMatchingConfirmation() {
        assertThatThrownBy(() -> service.resetPassword("tenant@example.test", UUID.randomUUID(), "newPassword123", "different"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khớp");
    }

    private User tenant() {
        User user = User.builder()
                .email("tenant@example.test")
                .fullName("Tenant A")
                .phone("0901234567")
                .passwordHash("old-password")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(User.Role.TENANT))
                .build();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return user;
    }

    private PasswordResetCode activeCode() {
        PasswordResetCode code = new PasswordResetCode();
        code.setId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        code.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        code.setEmail("tenant@example.test");
        code.setCodeHash("hashed-code");
        code.setExpiresAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusMinutes(10));
        return code;
    }

    private static class TestPasswordResetEmailService extends AutomationEmailService {
        String toEmail;
        String fullName;
        String code;
        int ttlMinutes;

        TestPasswordResetEmailService() {
            super(
                    mock(AutomationSettingRepository.class),
                    mock(UserRepository.class),
                    mock(RoomRepository.class)
            );
        }

        @Override
        public void sendPasswordResetCode(String toEmail, String fullName, String code, int ttlMinutes) {
            this.toEmail = toEmail;
            this.fullName = fullName;
            this.code = code;
            this.ttlMinutes = ttlMinutes;
        }
    }
}
