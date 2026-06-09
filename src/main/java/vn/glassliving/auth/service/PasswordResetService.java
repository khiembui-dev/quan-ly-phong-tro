package vn.glassliving.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.PasswordResetCode;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.PasswordResetCodeRepository;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.automation.service.AutomationEmailService;
import vn.glassliving.common.exception.BusinessException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    public static final int CODE_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CODE = Pattern.compile("^\\d{6}$");

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AutomationEmailService emailService;
    private final PasswordResetCodeGenerator codeGenerator;
    private final Clock clock;

    @Transactional
    public void requestResetCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            return;
        }

        String code = codeGenerator.generate();
        PasswordResetCode resetCode = new PasswordResetCode();
        resetCode.setUserId(user.getId());
        resetCode.setEmail(email);
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setExpiresAt(now().plusMinutes(CODE_TTL_MINUTES));
        resetCode.setAttemptCount(0);
        codeRepository.save(resetCode);

        emailService.sendPasswordResetCode(email, user.getFullName(), code, CODE_TTL_MINUTES);
    }

    @Transactional
    public VerifiedReset verifyCode(String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = normalizeCode(rawCode);
        PasswordResetCode resetCode = codeRepository
                .findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(email, now())
                .orElseThrow(() -> BusinessException.badRequest("Mã xác nhận đã hết hạn hoặc không tồn tại."));

        if (resetCode.getAttemptCount() >= MAX_ATTEMPTS) {
            resetCode.setConsumedAt(now());
            codeRepository.save(resetCode);
            throw BusinessException.badRequest("Mã xác nhận đã bị khóa do nhập sai quá nhiều lần.");
        }

        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            resetCode.setAttemptCount(resetCode.getAttemptCount() + 1);
            resetCode.setLastAttemptAt(now());
            if (resetCode.getAttemptCount() >= MAX_ATTEMPTS) {
                resetCode.setConsumedAt(now());
            }
            codeRepository.save(resetCode);
            throw BusinessException.badRequest("Mã xác nhận không đúng.");
        }

        return new VerifiedReset(email, resetCode.getId());
    }

    @Transactional
    public void resetPassword(String rawEmail, UUID resetCodeId, String newPassword, String confirmPassword) {
        String email = normalizeEmail(rawEmail);
        validatePassword(newPassword, confirmPassword);
        if (resetCodeId == null) {
            throw BusinessException.badRequest("Phiên đặt lại mật khẩu không hợp lệ.");
        }

        PasswordResetCode resetCode = codeRepository.findByIdAndEmailAndConsumedAtIsNull(resetCodeId, email)
                .orElseThrow(() -> BusinessException.badRequest("Phiên đặt lại mật khẩu không hợp lệ hoặc đã được dùng."));
        if (resetCode.getExpiresAt().isBefore(now())) {
            throw BusinessException.badRequest("Mã xác nhận đã hết hạn.");
        }

        User user = userRepository.findById(resetCode.getUserId())
                .orElseThrow(() -> BusinessException.badRequest("Tài khoản không hợp lệ."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetCode.setConsumedAt(now());
        codeRepository.save(resetCode);
    }

    private void validatePassword(String newPassword, String confirmPassword) {
        String password = Objects.toString(newPassword, "");
        if (password.length() < 8 || password.length() > 64) {
            throw BusinessException.badRequest("Mật khẩu mới phải có từ 8 đến 64 ký tự.");
        }
        if (!password.equals(Objects.toString(confirmPassword, ""))) {
            throw BusinessException.badRequest("Mật khẩu xác nhận không khớp.");
        }
    }

    private String normalizeEmail(String rawEmail) {
        String email = Objects.toString(rawEmail, "").trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 160 || !EMAIL.matcher(email).matches()) {
            throw BusinessException.badRequest("Email không hợp lệ.");
        }
        return email;
    }

    private String normalizeCode(String rawCode) {
        String code = Objects.toString(rawCode, "").trim();
        if (!CODE.matcher(code).matches()) {
            throw BusinessException.badRequest("Mã xác nhận phải gồm 6 chữ số.");
        }
        return code;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public record VerifiedReset(String email, UUID resetCodeId) {}
}
