package vn.glassliving.maintenance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.service.NotificationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceTicketRepository ticketRepository;
    private final NotificationService notificationService;

    @Transactional
    public MaintenanceTicket create(UUID ownerId,
                                    UUID propertyId, UUID roomId,
                                    String category, String priority,
                                    String title, String description,
                                    BigDecimal estimatedCost) {
        return createInternal(ownerId, ownerId, propertyId, roomId, category, priority, title, description, estimatedCost);
    }

    @Transactional
    public MaintenanceTicket createForReporter(UUID reporterUserId,
                                               UUID ownerId,
                                               UUID propertyId, UUID roomId,
                                               String category, String priority,
                                               String title, String description) {
        return createInternal(ownerId, reporterUserId, propertyId, roomId, category, priority, title, description, null);
    }

    private MaintenanceTicket createInternal(UUID ownerId,
                                             UUID reporterUserId,
                                             UUID propertyId, UUID roomId,
                                             String category, String priority,
                                             String title, String description,
                                             BigDecimal estimatedCost) {
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("Tiêu đề không được trống.");
        }
        String code = "MT-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"
                + String.format("%04d", (int) (Math.random() * 9000) + 1000);

        MaintenanceTicket t = MaintenanceTicket.builder()
                .code(code)
                .ownerId(ownerId)
                .propertyId(propertyId)
                .roomId(roomId)
                .reporterUserId(reporterUserId)
                .category(MaintenanceTicket.Category.valueOf(category != null ? category : "OTHER"))
                .priority(MaintenanceTicket.Priority.valueOf(priority != null ? priority : "NORMAL"))
                .status(MaintenanceTicket.Status.OPEN)
                .title(title.trim())
                .description(description)
                .estimatedCost(estimatedCost)
                .reportedAt(OffsetDateTime.now())
                .build();
        t = ticketRepository.save(t);

        notificationService.create(ownerId, "MAINTENANCE_NEW",
                "Ticket mới: " + t.getTitle(),
                "Mã: " + t.getCode() + " · Mức độ: " + t.getPriority(),
                "/admin/tickets");
        return t;
    }

    @Transactional
    public MaintenanceTicket transition(UUID ownerId, UUID id, String newStatus, String note) {
        MaintenanceTicket t = ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Ticket"));
        if (!t.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu yêu cầu này.");
        }
        MaintenanceTicket.Status next = MaintenanceTicket.Status.valueOf(newStatus);
        t.setStatus(next);
        if (next == MaintenanceTicket.Status.RESOLVED || next == MaintenanceTicket.Status.CLOSED) {
            if (t.getResolvedAt() == null) t.setResolvedAt(OffsetDateTime.now());
            if (note != null && !note.isBlank()) t.setResolutionNote(note);
        }
        return ticketRepository.save(t);
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
        return ticketRepository.save(t);
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
}
