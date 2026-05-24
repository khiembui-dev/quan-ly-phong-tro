package vn.glassliving.utility.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Periodic utility (electric / water) meter reading for a single room.
 * Quantity = curr - prev, amount = qty * unit price.
 * One reading per (room, year, month) — enforced by unique index.
 */
@Entity
@Table(name = "utility_reading")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE utility_reading SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class UtilityReading extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "period_year", nullable = false)
    private Short periodYear;

    @Column(name = "period_month", nullable = false)
    private Short periodMonth;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    // ----- Electricity -----
    @Column(name = "electric_prev", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal electricPrev = BigDecimal.ZERO;

    @Column(name = "electric_curr", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal electricCurr = BigDecimal.ZERO;

    @Column(name = "electric_unit_price", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal electricUnitPrice = new BigDecimal("4000");

    @Column(name = "electric_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal electricAmount = BigDecimal.ZERO;

    // ----- Water -----
    @Column(name = "water_prev", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waterPrev = BigDecimal.ZERO;

    @Column(name = "water_curr", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waterCurr = BigDecimal.ZERO;

    @Column(name = "water_unit_price", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal waterUnitPrice = new BigDecimal("25000");

    @Column(name = "water_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal waterAmount = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    /** Convenience: kWh used in this period (electricCurr - electricPrev). */
    @Transient
    public BigDecimal getElectricUsage() {
        if (electricCurr == null || electricPrev == null) return BigDecimal.ZERO;
        return electricCurr.subtract(electricPrev).max(BigDecimal.ZERO);
    }

    /** Convenience: m³ used. */
    @Transient
    public BigDecimal getWaterUsage() {
        if (waterCurr == null || waterPrev == null) return BigDecimal.ZERO;
        return waterCurr.subtract(waterPrev).max(BigDecimal.ZERO);
    }

    /** Convenience total for the period. */
    @Transient
    public BigDecimal getTotalAmount() {
        BigDecimal e = electricAmount != null ? electricAmount : BigDecimal.ZERO;
        BigDecimal w = waterAmount != null ? waterAmount : BigDecimal.ZERO;
        return e.add(w);
    }

    /** Recompute amounts based on current usage and unit prices. Call before save. */
    public void recompute() {
        BigDecimal eUsage = getElectricUsage();
        BigDecimal wUsage = getWaterUsage();
        electricAmount = eUsage.multiply(electricUnitPrice != null ? electricUnitPrice : BigDecimal.ZERO).setScale(0, java.math.RoundingMode.HALF_UP);
        waterAmount    = wUsage.multiply(waterUnitPrice    != null ? waterUnitPrice    : BigDecimal.ZERO).setScale(0, java.math.RoundingMode.HALF_UP);
    }
}
