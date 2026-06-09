package vn.glassliving.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.payment.entity.Payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
    List<Payment> findTop10ByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    Optional<Payment> findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
            UUID invoiceId, UUID userId, Payment.PaymentStatus status);
    Optional<Payment> findFirstByInvoiceIdAndUserIdAndStatusInOrderByCreatedAtDesc(
            UUID invoiceId, UUID userId, Collection<Payment.PaymentStatus> statuses);
    Optional<Payment> findFirstByInvoiceIdAndUserIdOrderByCreatedAtDesc(UUID invoiceId, UUID userId);
    boolean existsByCode(String code);
    boolean existsByGatewayTxnIdAndStatus(String gatewayTxnId, Payment.PaymentStatus status);
    boolean existsByBankTransactionNumberAndStatus(String bankTransactionNumber, Payment.PaymentStatus status);
}
