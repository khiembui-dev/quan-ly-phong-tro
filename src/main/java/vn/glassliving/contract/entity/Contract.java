package vn.glassliving.contract.entity;

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
@Table(name = "contract")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE contract SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Contract extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "booking_id", unique = true)
    private UUID bookingId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "duration_months", nullable = false)
    private Short durationMonths;

    @Column(name = "rent_monthly", nullable = false, precision = 14, scale = 0)
    private BigDecimal rentMonthly;

    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "service_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "electric_unit", nullable = false, precision = 8, scale = 0)
    private BigDecimal electricUnit;

    @Column(name = "water_unit", nullable = false, precision = 8, scale = 0)
    private BigDecimal waterUnit;

    @Column(name = "billing_day", nullable = false)
    @Builder.Default
    private Short billingDay = 1;

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "owner_signed_at")
    private OffsetDateTime ownerSignedAt;

    @Column(name = "tenant_signed_at")
    private OffsetDateTime tenantSignedAt;

    @Column(name = "signature_otp_hash", length = 120)
    private String signatureOtpHash;

    @Column(name = "terminated_at")
    private OffsetDateTime terminatedAt;

    @Column(name = "terminated_reason", length = 240)
    private String terminatedReason;

    @Column(name = "early_termination_fee", precision = 14, scale = 0)
    private BigDecimal earlyTerminationFee;

    @Column(name = "extra_terms", columnDefinition = "TEXT")
    private String extraTerms;

    public enum ContractStatus { DRAFT, AWAITING_SIGN, ACTIVE, EXPIRING_SOON, EXPIRED, TERMINATED }
}
