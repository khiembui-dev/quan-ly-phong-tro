package vn.glassliving.admin.page.tickets;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.service.MaintenanceService;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping({"/admin/maintenance", "/admin/tickets"})
@RequiredArgsConstructor
public class TicketsActionController {

    private final MaintenanceService maintenanceService;

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @RequestParam(required = false) UUID propertyId,
                         @RequestParam(required = false) UUID roomId,
                         @RequestParam(required = false, defaultValue = "OTHER") String category,
                         @RequestParam(required = false, defaultValue = "NORMAL") String priority,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) BigDecimal estimatedCost,
                         RedirectAttributes ra) {
        try {
            MaintenanceTicket t = maintenanceService.create(me.getId(), propertyId, roomId,
                    category, priority, title, description, estimatedCost);
            FlashAlert.ok(ra, "Đã tạo ticket \"" + t.getCode() + "\".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Dữ liệu không hợp lệ: " + ex.getMessage());
        }
        return "redirect:/admin/tickets";
    }

    @PostMapping("/{id}/status")
    public String transition(@AuthenticationPrincipal AppUserDetails me,
                             @PathVariable UUID id,
                             @RequestParam String status,
                             @RequestParam(required = false) String note,
                             RedirectAttributes ra) {
        try {
            MaintenanceTicket t = maintenanceService.transition(me.getId(), id, status, note);
            FlashAlert.ok(ra, "Đã cập nhật trạng thái: " + statusLabel(t.getStatus()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Trạng thái không hợp lệ.");
        }
        return "redirect:/admin/tickets";
    }

    @PostMapping("/{id}/assign")
    public String assign(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         @RequestParam UUID assigneeId,
                         RedirectAttributes ra) {
        try {
            maintenanceService.assign(me.getId(), id, assigneeId);
            FlashAlert.ok(ra, "Đã gán người phụ trách.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tickets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         RedirectAttributes ra) {
        try {
            maintenanceService.delete(me.getId(), id);
            FlashAlert.ok(ra, "Đã xóa ticket.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tickets";
    }

    private static String statusLabel(MaintenanceTicket.Status s) {
        return switch (s) {
            case OPEN            -> "Mới";
            case ACKNOWLEDGED    -> "Đã ghi nhận";
            case IN_PROGRESS     -> "Đang xử lý";
            case AWAITING_PARTS  -> "Chờ phụ tùng";
            case RESOLVED        -> "Đã hoàn thành";
            case CLOSED          -> "Đã đóng";
            case CANCELLED       -> "Đã hủy";
        };
    }
}
