package vn.glassliving.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE booking SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Booking extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.DRAFT;

    @Column(name = "cccd_number_enc", columnDefinition = "TEXT")
    private String cccdNumberEnc;

    @Column(name = "cccd_front_url", columnDefinition = "TEXT")
    private String cccdFrontUrl;

    @Column(name = "cccd_back_url", columnDefinition = "TEXT")
    private String cccdBackUrl;

    @Column(name = "occupation", length = 120)
    private String occupation;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "duration_months", nullable = false)
    private Short durationMonths;

    @Column(name = "occupant_count", nullable = false)
    @Builder.Default
    private Short occupantCount = 1;

    @Column(name = "has_pet", nullable = false)
    private boolean hasPet;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "rent_monthly", nullable = false, precision = 14, scale = 0)
    private BigDecimal rentMonthly;

    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "service_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "total_due", nullable = false, precision = 14, scale = 0)
    private BigDecimal totalDue;

    @Column(name = "payment_method", length = 24)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 240)
    private String cancelReason;

    public enum BookingStatus { DRAFT, PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED }
    public enum PaymentMethod { VNPAY, MOMO, BANK_TRANSFER, CASH }
}
