package vn.glassliving.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.notification.entity.Notification;
import vn.glassliving.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID userId) {
        return repository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification create(UUID userId, String type, String title, String body, String linkUrl) {
        return create(userId, type, title, body, linkUrl, null);
    }

    @Transactional
    public Notification create(UUID userId, String type, String title, String body, String linkUrl, String imageUrl) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .linkUrl(linkUrl)
                .imageUrl(imageUrl)
                .build();
        return repository.save(n);
    }

    @Transactional
    public int createForUsers(List<UUID> userIds, String type, String title, String body, String linkUrl, String imageUrl) {
        return createForUsers(userIds, type, title, body, linkUrl, imageUrl, null, null);
    }

    @Transactional
    public int createForUsers(List<UUID> userIds,
                              String type,
                              String title,
                              String body,
                              String linkUrl,
                              String imageUrl,
                              UUID senderUserId,
                              UUID batchId) {
        if (userIds == null || userIds.isEmpty()) return 0;
        List<Notification> notifications = userIds.stream()
                .distinct()
                .map(userId -> Notification.builder()
                        .userId(userId)
                        .batchId(batchId)
                        .senderUserId(senderUserId)
                        .type(type)
                        .title(title)
                        .body(body)
                        .linkUrl(linkUrl)
                        .imageUrl(imageUrl)
                        .build())
                .toList();
        repository.saveAll(notifications);
        return notifications.size();
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, OffsetDateTime.now());
    }

    @Transactional
    public void markRead(UUID id) {
        repository.findById(id).ifPresent(n -> {
            if (n.getReadAt() == null) n.setReadAt(OffsetDateTime.now());
        });
    }
}
