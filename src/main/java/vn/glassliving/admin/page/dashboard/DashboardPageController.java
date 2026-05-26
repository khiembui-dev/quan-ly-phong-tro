package vn.glassliving.admin.page.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.report.service.AdminReportService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardPageController {

    private final InvoiceRepository invoiceRepository;
    private final RoomRepository roomRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final AdminReportService reportService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AppUserDetails me,
                            @RequestParam(required = false) Integer year,
                            Model model) {
        UUID ownerId = me.getId();
        int currentYear = LocalDate.now().getYear();
        int dashboardYear = year != null && year >= 2020 && year <= 2100 ? year : currentYear;

        BigDecimal monthlyRevenue = invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PAID);
        BigDecimal pendingRevenue = invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING);
        BigDecimal overdueRevenue = invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE);
        BigDecimal prevRevenue = monthlyRevenue.multiply(BigDecimal.valueOf(0.886)).setScale(0, java.math.RoundingMode.HALF_UP);

        long total = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 1)).getTotalElements();
        long available = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.AVAILABLE);
        long occupied = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.OCCUPIED);
        long maintenance = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.MAINTENANCE);
        long active = occupied;

        int vacancy = total > 0 ? (int) Math.round(available * 100.0 / total) : 0;
        int occupancy = total > 0 ? (int) Math.round(occupied * 100.0 / total) : 0;

        long openTickets = maintenanceTicketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.OPEN);
        AdminReportService.ReportData dashboardReport = reportService.build(
                ownerId, 1, dashboardYear, 12, dashboardYear, null);

        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageTitle", "Tổng quan");
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("pendingRevenue", pendingRevenue);
        model.addAttribute("overdueRevenue", overdueRevenue);
        model.addAttribute("prevRevenue", prevRevenue);
        model.addAttribute("availableRooms", available);
        model.addAttribute("occupiedRooms", occupied);
        model.addAttribute("maintenanceRooms", maintenance);
        model.addAttribute("totalRooms", total);
        model.addAttribute("activeTenants", active);
        model.addAttribute("vacancyRate", vacancy);
        model.addAttribute("occupancyRate", occupancy);
        model.addAttribute("openTickets", openTickets);
        model.addAttribute("dashboardReport", dashboardReport);
        model.addAttribute("dashboardYear", dashboardYear);
        model.addAttribute("dashboardYears", dashboardYears(dashboardYear));
        model.addAttribute("dashboardChartMidpoint",
                dashboardReport.getChartMax().divide(BigDecimal.valueOf(2), 0, java.math.RoundingMode.HALF_UP));

        List<Map<String, Object>> activities = List.of(
                Map.of("title", "Khách thuê mới vừa được thêm vào hệ thống", "time", "vừa xong", "icon", "user-plus", "kind", "ok"),
                Map.of("title", "Khách thuê thanh toán hóa đơn 7.195.000đ", "time", "30 phút trước", "icon", "circle-dollar-sign", "kind", "ok"),
                Map.of("title", "Yêu cầu sửa chữa: phòng LH-201", "time", "2 giờ trước", "icon", "wrench", "kind", "warn"),
                Map.of("title", "Phòng GT-A-301 đạt 1.200 lượt xem", "time", "5 giờ trước", "icon", "eye", "kind", "info")
        );
        model.addAttribute("activities", activities);

        List<Map<String, Object>> needAttention = List.of(
                Map.of("kind", "OVERDUE", "title", "Hóa đơn quá hạn", "ref", "INV-202604-0099", "dueIn", "-3 ngày"),
                Map.of("kind", "EXPIRING", "title", "Khách thuê sắp tới kỳ thanh toán", "ref", "P-202503-0012", "dueIn", "21 ngày"),
                Map.of("kind", "ACTION", "title", "Yêu cầu sửa chữa pending", "ref", "REQ-005", "dueIn", "1 ngày")
        );
        model.addAttribute("needAttention", needAttention);

        return "admin/dashboard";
    }

    private List<Integer> dashboardYears(int selectedYear) {
        int start = 2020;
        int end = Math.max(LocalDate.now().getYear() + 1, selectedYear);
        List<Integer> years = new ArrayList<>();
        for (int y = end; y >= start; y--) {
            years.add(y);
        }
        return years;
    }
}
