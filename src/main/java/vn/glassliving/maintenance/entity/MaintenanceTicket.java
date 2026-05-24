package vn.glassliving.maintenance.entity;

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
@Table(name = "maintenance_ticket")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE maintenance_ticket SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class MaintenanceTicket extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "property_id")
    private UUID propertyId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id")
    private UUID roomId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "reporter_user_id")
    private UUID reporterUserId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "category", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "priority", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_cost", precision = 14, scale = 0)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 14, scale = 0)
    private BigDecimal actualCost;

    /** Comma-separated photo URLs (simple, no FK). */
    @Column(name = "photo_urls", columnDefinition = "TEXT")
    private String photoUrls;

    @Column(name = "reported_at", nullable = false)
    @Builder.Default
    private OffsetDateTime reportedAt = OffsetDateTime.now();

    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    public enum Category { ELECTRICAL, PLUMBING, AC_HVAC, FURNITURE, APPLIANCE, INTERNET, CLEANING, SECURITY, OTHER }
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status   { OPEN, ACKNOWLEDGED, IN_PROGRESS, AWAITING_PARTS, RESOLVED, CLOSED, CANCELLED }
}
