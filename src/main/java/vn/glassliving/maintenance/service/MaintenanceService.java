package vn.glassliving.maintenance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.entity.MaintenanceTicketMessage;
import vn.glassliving.maintenance.repository.MaintenanceTicketMessageRepository;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.service.NotificationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceTicketRepository ticketRepository;
    private final MaintenanceTicketMessageRepository messageRepository;
    private final NotificationService notificationService;

    @Transactional
    public MaintenanceTicket create(UUID ownerId,
                                    UUID propertyId, UUID roomId,
                                    String category, String priority,
                                    String title, String description,
                                    BigDecimal estimatedCost) {
        return createInternal(ownerId, ownerId, propertyId, roomId, category, priority, title, description, estimatedCost, null);
    }

    @Transactional
    public MaintenanceTicket create(UUID ownerId,
                                    UUID propertyId, UUID roomId,
                                    String category, String priority,
                                    String title, String description,
                                    BigDecimal estimatedCost,
                                    String photoUrls) {
        return createInternal(ownerId, ownerId, propertyId, roomId, category, priority, title, description, estimatedCost, photoUrls);
    }

    @Transactional
    public MaintenanceTicket createForReporter(UUID reporterUserId,
                                               UUID ownerId,
                                               UUID propertyId, UUID roomId,
                                               String category, String priority,
                                               String title, String description) {
        return createInternal(ownerId, reporterUserId, propertyId, roomId, category, priority, title, description, null, null);
    }

    @Transactional
    public MaintenanceTicket createForReporter(UUID reporterUserId,
                                               UUID ownerId,
                                               UUID propertyId, UUID roomId,
                                               String category, String priority,
                                               String title, String description,
                                               String photoUrls) {
        return createInternal(ownerId, reporterUserId, propertyId, roomId, category, priority, title, description, null, photoUrls);
    }

    private MaintenanceTicket createInternal(UUID ownerId,
                                             UUID reporterUserId,
                                             UUID propertyId, UUID roomId,
                                             String category, String priority,
                                             String title, String description,
                                             BigDecimal estimatedCost,
                                             String photoUrls) {
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("Tiêu đề không được trống.");
        }
        String code = "MT-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"
                + String.format("%04d", (int) (Math.random() * 9000) + 1000);
        MaintenanceTicket.Category resolvedCategory = resolveCategory(category, title, description);
        MaintenanceTicket.Priority resolvedPriority = resolvePriority(priority, title, description, resolvedCategory);

        MaintenanceTicket t = MaintenanceTicket.builder()
                .code(code)
                .ownerId(ownerId)
                .propertyId(propertyId)
                .roomId(roomId)
                .reporterUserId(reporterUserId)
                .category(resolvedCategory)
                .priority(resolvedPriority)
                .status(MaintenanceTicket.Status.OPEN)
                .title(title.trim())
                .description(description)
                .estimatedCost(estimatedCost)
                .photoUrls(cleanPhotoUrls(photoUrls))
                .reportedAt(OffsetDateTime.now())
                .build();
        t = ticketRepository.save(t);
        if (reporterUserId != null) {
            saveMessage(t.getId(),
                    reporterUserId,
                    reporterUserId.equals(ownerId)
                            ? MaintenanceTicketMessage.SenderRole.OWNER
                            : MaintenanceTicketMessage.SenderRole.TENANT,
                    firstNonBlank(description, title),
                    photoUrls);
        }

        notificationService.create(ownerId, "MAINTENANCE_NEW",
                "Ticket mới: " + t.getTitle(),
                "Mã: " + t.getCode() + " · Mức độ: " + t.getPriority(),
                "/admin/tickets");
        if (reporterUserId != null && !reporterUserId.equals(ownerId)) {
            notificationService.create(reporterUserId, "MAINTENANCE_CREATED",
                    "Đã gửi ticket " + t.getCode(),
                    "Chúng tôi đã ghi nhận yêu cầu và sẽ cập nhật tiến độ tại khu vực khách thuê.",
                    "/customer/notifications#tickets");
        }
        return t;
    }

    @Transactional
    public MaintenanceTicketMessage addReporterMessage(UUID reporterUserId,
                                                       UUID ticketId,
                                                       String body,
                                                       String photoUrls) {
        MaintenanceTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (ticket.getReporterUserId() == null || !ticket.getReporterUserId().equals(reporterUserId)) {
            throw BusinessException.forbidden("Bạn không có quyền phản hồi ticket này.");
        }
        if (isBlank(body) && isBlank(photoUrls)) {
            throw BusinessException.badRequest("Vui lòng nhập nội dung hoặc đính kèm ảnh.");
        }
        if (ticket.getStatus() == MaintenanceTicket.Status.RESOLVED
                || ticket.getStatus() == MaintenanceTicket.Status.CLOSED
                || ticket.getStatus() == MaintenanceTicket.Status.CANCELLED) {
            ticket.setStatus(MaintenanceTicket.Status.OPEN);
            ticket.setResolvedAt(null);
            ticketRepository.save(ticket);
        }
        MaintenanceTicketMessage message = saveMessage(ticket.getId(),
                reporterUserId,
                MaintenanceTicketMessage.SenderRole.TENANT,
                body,
                photoUrls);
        notificationService.create(ticket.getOwnerId(), "MAINTENANCE_REPLY",
                "Khách phản hồi ticket " + ticket.getCode(),
                firstNonBlank(body, "Khách vừa gửi thêm hình ảnh hoặc cập nhật mới."),
                "/admin/tickets");
        return message;
    }

    @Transactional
    public MaintenanceTicketMessage addOwnerMessage(UUID ownerId,
                                                   UUID ticketId,
                                                   String body,
                                                   String photoUrls) {
        MaintenanceTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (!ticket.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("B\u1ea1n kh\u00f4ng s\u1edf h\u1eefu y\u00eau c\u1ea7u n\u00e0y.");
        }
        if (isBlank(body) && isBlank(photoUrls)) {
            throw BusinessException.badRequest("Vui l\u00f2ng nh\u1eadp n\u1ed9i dung ho\u1eb7c \u0111\u00ednh k\u00e8m \u1ea3nh.");
        }
        if (ticket.getStatus() == MaintenanceTicket.Status.OPEN
                || ticket.getStatus() == MaintenanceTicket.Status.RESOLVED
                || ticket.getStatus() == MaintenanceTicket.Status.CLOSED
                || ticket.getStatus() == MaintenanceTicket.Status.CANCELLED) {
            ticket.setStatus(MaintenanceTicket.Status.ACKNOWLEDGED);
            ticket.setResolvedAt(null);
            ticketRepository.save(ticket);
        }

        MaintenanceTicketMessage message = saveMessage(ticket.getId(),
                ownerId,
                MaintenanceTicketMessage.SenderRole.OWNER,
                body,
                photoUrls);
        if (ticket.getReporterUserId() != null && !ticket.getReporterUserId().equals(ownerId)) {
            notificationService.create(ticket.getReporterUserId(), "MAINTENANCE_REPLY",
                    "Ch\u1ee7 tr\u1ecd ph\u1ea3n h\u1ed3i ticket " + ticket.getCode(),
                    firstNonBlank(body, "Ch\u1ee7 tr\u1ecd v\u1eeba g\u1eedi th\u00eam h\u00ecnh \u1ea3nh ho\u1eb7c c\u1eadp nh\u1eadt m\u1edbi."),
                    "/customer/notifications#ticket-" + ticket.getId());
        }
        return message;
    }

    @Transactional
    public MaintenanceTicket transition(UUID ownerId, UUID id, String newStatus, String note) {
        MaintenanceTicket t = ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (!t.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu yêu cầu này.");
        }
        MaintenanceTicket.Status next = MaintenanceTicket.Status.valueOf(newStatus);
        MaintenanceTicket.Status previous = t.getStatus();
        t.setStatus(next);
        if (next == MaintenanceTicket.Status.RESOLVED || next == MaintenanceTicket.Status.CLOSED) {
            if (t.getResolvedAt() == null) t.setResolvedAt(OffsetDateTime.now());
            if (note != null && !note.isBlank()) t.setResolutionNote(note);
        }
        t = ticketRepository.save(t);
        if (note != null && !note.isBlank()) {
            saveMessage(t.getId(),
                    ownerId,
                    MaintenanceTicketMessage.SenderRole.OWNER,
                    note,
                    null);
        }
        if (t.getReporterUserId() != null && !t.getReporterUserId().equals(ownerId) && previous != next) {
            notificationService.create(t.getReporterUserId(), "MAINTENANCE_STATUS",
                    "Ticket " + t.getCode() + ": " + statusLabel(next),
                    note != null && !note.isBlank() ? note.trim() : "Trạng thái yêu cầu của bạn đã được cập nhật.",
                    "/customer/notifications#tickets");
        }
        return t;
    }

    @Transactional
    public MaintenanceTicket assign(UUID ownerId, UUID id, UUID assigneeId) {
        MaintenanceTicket t = ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (!t.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu yêu cầu này.");
        }
        t.setAssigneeUserId(assigneeId);
        if (t.getStatus() == MaintenanceTicket.Status.OPEN) {
            t.setStatus(MaintenanceTicket.Status.ACKNOWLEDGED);
        }
        t = ticketRepository.save(t);
        if (t.getReporterUserId() != null && !t.getReporterUserId().equals(ownerId)) {
            notificationService.create(t.getReporterUserId(), "MAINTENANCE_ASSIGNED",
                    "Ticket " + t.getCode() + " đã được tiếp nhận",
                    "Yêu cầu của bạn đã được đưa vào hàng xử lý.",
                    "/customer/notifications#tickets");
        }
        return t;
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        MaintenanceTicket t = ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (!t.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu yêu cầu này.");
        }
        ticketRepository.delete(t);
    }

    private static MaintenanceTicket.Category resolveCategory(String category, String title, String description) {
        if (category != null && !category.isBlank() && !"OTHER".equalsIgnoreCase(category.trim())) {
            try {
                return MaintenanceTicket.Category.valueOf(category.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to smart inference
            }
        }
        String text = normalize(title + " " + Objects.toString(description, ""));
        if (containsAny(text, "dien", "chap", "cau dao", "cong tac", "bong den", "mat dien")) return MaintenanceTicket.Category.ELECTRICAL;
        if (containsAny(text, "nuoc", "ong", "ro ri", "bon cau", "voi", "ngap", "thoat nuoc")) return MaintenanceTicket.Category.PLUMBING;
        if (containsAny(text, "may lanh", "dieu hoa", "nong", "khong lanh", "ac")) return MaintenanceTicket.Category.AC_HVAC;
        if (containsAny(text, "wifi", "internet", "mang", "router")) return MaintenanceTicket.Category.INTERNET;
        if (containsAny(text, "cua", "giuong", "tu", "ban", "ghe", "noi that")) return MaintenanceTicket.Category.FURNITURE;
        if (containsAny(text, "tu lanh", "may giat", "bep", "thiet bi")) return MaintenanceTicket.Category.APPLIANCE;
        if (containsAny(text, "ve sinh", "rac", "mui", "don dep")) return MaintenanceTicket.Category.CLEANING;
        if (containsAny(text, "an ninh", "khoa", "mat cap", "camera")) return MaintenanceTicket.Category.SECURITY;
        return MaintenanceTicket.Category.OTHER;
    }

    private static MaintenanceTicket.Priority resolvePriority(String priority, String title, String description,
                                                              MaintenanceTicket.Category category) {
        if (priority != null && !priority.isBlank() && !"NORMAL".equalsIgnoreCase(priority.trim())) {
            try {
                return MaintenanceTicket.Priority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to smart inference
            }
        }
        String text = normalize(title + " " + Objects.toString(description, ""));
        if (containsAny(text, "nguy hiem", "chap dien", "chay", "no", "ro gas", "vo ong", "ngap", "mat dien", "mat nuoc")) {
            return MaintenanceTicket.Priority.URGENT;
        }
        if (category == MaintenanceTicket.Category.ELECTRICAL || category == MaintenanceTicket.Category.PLUMBING
                || category == MaintenanceTicket.Category.SECURITY) {
            return MaintenanceTicket.Priority.HIGH;
        }
        if (containsAny(text, "khong dung duoc", "hong nang", "gap", "rat gap")) return MaintenanceTicket.Priority.HIGH;
        return MaintenanceTicket.Priority.NORMAL;
    }

    private static String cleanPhotoUrls(String value) {
        if (value == null || value.isBlank()) return null;
        String joined = java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private MaintenanceTicketMessage saveMessage(UUID ticketId,
                                                 UUID senderUserId,
                                                 MaintenanceTicketMessage.SenderRole senderRole,
                                                 String body,
                                                 String photoUrls) {
        String cleanBody = firstNonBlank(body);
        String cleanPhotos = cleanPhotoUrls(photoUrls);
        if (cleanBody == null && cleanPhotos == null) {
            return null;
        }
        return messageRepository.save(MaintenanceTicketMessage.builder()
                .ticketId(ticketId)
                .senderUserId(senderUserId)
                .senderRole(senderRole)
                .body(cleanBody)
                .photoUrls(cleanPhotos)
                .build());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String statusLabel(MaintenanceTicket.Status s) {
        return switch (s) {
            case OPEN -> "Mới";
            case ACKNOWLEDGED -> "Đã ghi nhận";
            case IN_PROGRESS -> "Đang xử lý";
            case AWAITING_PARTS -> "Chờ vật tư";
            case RESOLVED -> "Đã hoàn thành";
            case CLOSED -> "Đã đóng";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        String text = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
