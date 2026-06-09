package vn.glassliving.maintenance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "maintenance_ticket_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE maintenance_ticket_message SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class MaintenanceTicketMessage extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Column(name = "sender_role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SenderRole senderRole;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "photo_urls", columnDefinition = "TEXT")
    private String photoUrls;

    public enum SenderRole { TENANT, OWNER, STAFF, SYSTEM }
}
