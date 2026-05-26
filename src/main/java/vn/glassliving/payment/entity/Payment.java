package vn.glassliving.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE payment SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Payment extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "booking_id")
    private UUID bookingId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "method", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(name = "gateway_txn_id", length = 120)
    private String gatewayTxnId;

    @Column(name = "gateway_payload", columnDefinition = "TEXT")
    private String gatewayPayload;

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "note", length = 240)
    private String note;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    public enum PaymentMethod { VNPAY, MOMO, BANK_TRANSFER, CASH }
    public enum PaymentStatus { PENDING, SUCCESS, FAILED, REFUNDED }
}
