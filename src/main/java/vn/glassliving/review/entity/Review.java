package vn.glassliving.review.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "review")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE review SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Review extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "owner_reply", columnDefinition = "TEXT")
    private String ownerReply;

    @Column(name = "owner_replied_at")
    private OffsetDateTime ownerRepliedAt;
}
