package vn.glassliving.invoice.entity;

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
@Table(name = "invoice")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE invoice SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Invoice extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "period_year", nullable = false)
    private Short periodYear;

    @Column(name = "period_month", nullable = false)
    private Short periodMonth;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "rent_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal rentAmount;

    @Column(name = "service_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal serviceAmount = BigDecimal.ZERO;

    @Column(name = "electric_prev", precision = 10, scale = 0)
    private BigDecimal electricPrev;

    @Column(name = "electric_curr", precision = 10, scale = 0)
    private BigDecimal electricCurr;

    @Column(name = "electric_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal electricAmount = BigDecimal.ZERO;

    @Column(name = "water_prev", precision = 10, scale = 0)
    private BigDecimal waterPrev;

    @Column(name = "water_curr", precision = 10, scale = 0)
    private BigDecimal waterCurr;

    @Column(name = "water_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal waterAmount = BigDecimal.ZERO;

    @Column(name = "other_items", columnDefinition = "TEXT")
    private String otherItemsJson;

    @Column(name = "other_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal otherAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "late_fee_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "last_reminder_at")
    private OffsetDateTime lastReminderAt;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    public enum InvoiceStatus { PENDING, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }
}
