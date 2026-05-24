package vn.glassliving.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "property")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE property SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Property extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "slug", nullable = false, length = 160, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address_line", nullable = false, length = 240)
    private String addressLine;

    @Column(name = "district", nullable = false, length = 80)
    private String district;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(name = "floor_plan_svg", columnDefinition = "TEXT")
    private String floorPlanSvg;

    @Column(name = "total_rooms", nullable = false)
    @Builder.Default
    private Integer totalRooms = 0;

    // ----- Tariff defaults (V3) -----
    @Column(name = "electric_unit", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal electricUnit = new BigDecimal("4000");

    @Column(name = "water_unit", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal waterUnit = new BigDecimal("25000");

    @Column(name = "service_fee_default", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal serviceFeeDefault = BigDecimal.ZERO;

    @Column(name = "internet_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal internetFee = BigDecimal.ZERO;

    @Column(name = "garbage_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal garbageFee = BigDecimal.ZERO;

    @Column(name = "management_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal managementFee = BigDecimal.ZERO;

    @Column(name = "billing_day_default", nullable = false)
    @Builder.Default
    private Short billingDayDefault = 1;

    /** Extra service fees defined by the owner — name + monthly amount, stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_fees", columnDefinition = "jsonb")
    @Builder.Default
    private List<ExtraFee> extraFees = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ExtraFee {
        private String name;
        private BigDecimal amount;
    }
}
