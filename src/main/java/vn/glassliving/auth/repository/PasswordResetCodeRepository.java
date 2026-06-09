package vn.glassliving.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.auth.entity.PasswordResetCode;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email,
            OffsetDateTime now);

    Optional<PasswordResetCode> findByIdAndEmailAndConsumedAtIsNull(UUID id, String email);
}
