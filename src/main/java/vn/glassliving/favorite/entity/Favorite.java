package vn.glassliving.favorite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "favorite")
@IdClass(Favorite.PK.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Favorite {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public static class PK implements Serializable {
        private UUID userId;
        private UUID roomId;
        public PK() {}
        public PK(UUID userId, UUID roomId) { this.userId = userId; this.roomId = roomId; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(roomId, pk.roomId);
        }
        @Override public int hashCode() { return Objects.hash(userId, roomId); }
    }
}
