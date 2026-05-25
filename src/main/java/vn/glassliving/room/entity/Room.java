package vn.glassliving.room.entity;

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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "room")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE room SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Room extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "slug", nullable = false, length = 160, unique = true)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "type", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    private RoomType type;

    @Column(name = "floor")
    private Short floor;

    @Column(name = "area_sqm", nullable = false, precision = 6, scale = 2)
    private BigDecimal areaSqm;

    @Column(name = "bedrooms", nullable = false)
    @Builder.Default
    private Short bedrooms = 1;

    @Column(name = "bathrooms", nullable = false)
    @Builder.Default
    private Short bathrooms = 1;

    @Column(name = "max_occupants", nullable = false)
    @Builder.Default
    private Short maxOccupants = 2;

    @Column(name = "price_monthly", nullable = false, precision = 14, scale = 0)
    private BigDecimal priceMonthly;

    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "service_fee", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "electric_unit", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal electricUnit = new BigDecimal("4000");

    @Column(name = "water_unit", nullable = false, precision = 8, scale = 0)
    @Builder.Default
    private BigDecimal waterUnit = new BigDecimal("25000");

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Column(name = "pet_allowed", nullable = false)
    private boolean petAllowed;

    @Column(name = "has_balcony", nullable = false)
    private boolean hasBalcony;

    @Column(name = "address_line", length = 240)
    private String addressLine;

    @Column(name = "district", nullable = false, length = 80)
    private String district;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** When status=OCCUPIED, the tenant currently living here. Synced from active contract. */
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "current_tenant_id")
    private UUID currentTenantId;

    @Column(name = "current_tenant_started_on")
    private LocalDate currentTenantStartedOn;

    /**
     * Inclusive date until which the current tenant has paid/covered room usage.
     * The admin tenant screen uses this to show days left before the next payment.
     */
    @Column(name = "current_tenant_paid_until")
    private LocalDate currentTenantPaidUntil;

    /** When true, electric/water unit prices are inherited from property; room values ignored. */
    @Column(name = "inherit_tariff", nullable = false)
    @Builder.Default
    private boolean inheritTariff = true;

    /** Optional named extra fees (parking, elevator, internet, etc.) — JSONB list of {name, amount}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_fees", columnDefinition = "jsonb")
    @Builder.Default
    private java.util.List<vn.glassliving.property.entity.Property.ExtraFee> extraFees = new java.util.ArrayList<>();

    /** User-defined amenities not in the seeded catalog — JSONB list of {name, category}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_amenities", columnDefinition = "jsonb")
    @Builder.Default
    private java.util.List<CustomAmenity> customAmenities = new java.util.ArrayList<>();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CustomAmenity {
        private String name;
        /** FURNITURE / UTILITY / RULE — category bucket the chip belongs to. */
        private String category;
    }

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "room_amenity",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    @Builder.Default
    private Set<Amenity> amenities = new HashSet<>();

    // Note: room_image is fetched via RoomImageRepository, not as a JPA relationship —
    // simpler than wiring a bidirectional @ManyToOne and avoids cascade/orphan complexity.

    public enum RoomType { STUDIO, DOUBLE, SINGLE, PENTHOUSE, APARTMENT, OTHER }
    public enum RoomStatus { AVAILABLE, OCCUPIED, MAINTENANCE, HIDDEN }
}
