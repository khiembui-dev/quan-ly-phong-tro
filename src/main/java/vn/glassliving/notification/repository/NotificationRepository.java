package vn.glassliving.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.glassliving.notification.entity.Notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<Notification> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") OffsetDateTime readAt);
}
