package vn.glassliving.admin.page.tickets;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.util.UUID;

@Controller
@RequestMapping({"/admin/maintenance", "/admin/tickets"})
@RequiredArgsConstructor
public class TicketsPageController {

    private final MaintenanceTicketRepository ticketRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails me,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "15") int size,
                       Model model) {
        UUID ownerId = me.getId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "reportedAt"));

        Page<MaintenanceTicket> ticketPage;
        if (status != null && !status.isBlank()) {
            ticketPage = ticketRepository.findByOwnerIdAndStatusOrderByReportedAtDesc(
                    ownerId, MaintenanceTicket.Status.valueOf(status), pageable);
        } else if (priority != null && !priority.isBlank()) {
            ticketPage = ticketRepository.findByOwnerIdAndPriorityOrderByReportedAtDesc(
                    ownerId, MaintenanceTicket.Priority.valueOf(priority), pageable);
        } else {
            ticketPage = ticketRepository.findByOwnerIdOrderByReportedAtDesc(ownerId, pageable);
        }

        long countAll        = ticketRepository.countByOwnerId(ownerId);
        long countOpen       = ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.OPEN);
        long countInProgress = ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.IN_PROGRESS);
        long countResolved   = ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.RESOLVED);
        long countUrgent     = ticketRepository.findByOwnerIdAndPriorityOrderByReportedAtDesc(
                ownerId, MaintenanceTicket.Priority.URGENT, PageRequest.of(0, 1)).getTotalElements();

        // Build room/property labels for display (simple maps)
        var rooms = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 500)).getContent();
        var roomLabels = new java.util.HashMap<UUID, String>();
        var roomCodes  = new java.util.HashMap<UUID, String>();
        for (var r : rooms) {
            roomLabels.put(r.getId(), r.getCode() + " · " + r.getTitle());
            roomCodes.put(r.getId(), r.getCode());
        }
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(ownerId);
        var propertyLabels = new java.util.HashMap<UUID, String>();
        for (var p : properties) propertyLabels.put(p.getId(), p.getName());

        model.addAttribute("activeNav", "maintenance");
        model.addAttribute("pageTitle", "Ticket");
        model.addAttribute("ticketPage", ticketPage);
        model.addAttribute("tickets", ticketPage.getContent());
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterPriority", priority);
        model.addAttribute("countAll", countAll);
        model.addAttribute("countOpen", countOpen);
        model.addAttribute("countInProgress", countInProgress);
        model.addAttribute("countResolved", countResolved);
        model.addAttribute("countUrgent", countUrgent);
        model.addAttribute("roomLabels", roomLabels);
        model.addAttribute("roomCodes", roomCodes);
        model.addAttribute("propertyLabels", propertyLabels);
        model.addAttribute("rooms", rooms);
        model.addAttribute("properties", properties);
        return "admin/maintenance";
    }
}
