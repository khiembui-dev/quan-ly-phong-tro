package vn.glassliving.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.payment.entity.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
    List<Payment> findTop10ByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    boolean existsByCode(String code);
}
