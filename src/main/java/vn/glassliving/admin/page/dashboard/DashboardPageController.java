package vn.glassliving.admin.page.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.report.service.AdminReportService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardPageController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final RoomRepository roomRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final ContractRepository contractRepository;
    private final AdminReportService reportService;
    private final MessageSource messageSource;

    @GetMapping({"", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal AppUserDetails me, Locale locale, Model model) {
        UUID ownerId = me.getId();
        invoiceService.refreshAgingStatuses(ownerId);
        LocalDate today = LocalDate.now();
        int dashboardYear = today.getYear();
        int focusMonth = today.getMonthValue();

        AdminReportService.ReportData dashboardReport = reportService.build(
                ownerId, 1, dashboardYear, 12, dashboardYear, null);
        AdminReportService.MonthRow focusRow = monthRow(dashboardReport.getMonths(), focusMonth);
        AdminReportService.MonthRow previousRow = monthRow(dashboardReport.getMonths(), Math.max(1, focusMonth - 1));

        BigDecimal monthlyRevenue = money(focusRow != null ? focusRow.getCollected() : BigDecimal.ZERO);
        BigDecimal monthBilled = money(focusRow != null ? focusRow.getRevenue() : BigDecimal.ZERO);
        BigDecimal monthOutstanding = money(focusRow != null ? focusRow.getOutstanding() : BigDecimal.ZERO);
        BigDecimal monthOperatingCost = money(focusRow != null ? focusRow.getOperatingCost() : BigDecimal.ZERO);
        BigDecimal prevRevenue = money(previousRow != null ? previousRow.getCollected() : BigDecimal.ZERO);
        BigDecimal monthTrendPercent = percentChange(prevRevenue, monthlyRevenue);
        boolean monthTrendUp = monthTrendPercent.compareTo(BigDecimal.ZERO) >= 0;

        BigDecimal pendingRevenue = sumInvoiceTotals(ownerId, Invoice.InvoiceStatus.PENDING, Invoice.InvoiceStatus.PARTIALLY_PAID);
        BigDecimal overdueRevenue = invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE);
        long pendingInvoiceCount = countInvoices(ownerId, Invoice.InvoiceStatus.PENDING, Invoice.InvoiceStatus.PARTIALLY_PAID);
        long overdueInvoiceCount = invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE);

        long total = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 1)).getTotalElements();
        long available = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.AVAILABLE);
        long occupied = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.OCCUPIED);
        long maintenance = roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.MAINTENANCE);
        long active = occupied;

        int vacancy = total > 0 ? (int) Math.round(available * 100.0 / total) : 0;
        int occupancy = total > 0 ? (int) Math.round(occupied * 100.0 / total) : 0;

        long openTickets = maintenanceTicketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.OPEN);
        long inProgressTickets = maintenanceTicketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.IN_PROGRESS);
        long urgentTickets = maintenanceTicketRepository.findByOwnerIdAndPriorityOrderByReportedAtDesc(
                ownerId, MaintenanceTicket.Priority.URGENT, PageRequest.of(0, 1)).getTotalElements();
        long activeTickets = countTickets(ownerId,
                MaintenanceTicket.Status.OPEN,
                MaintenanceTicket.Status.ACKNOWLEDGED,
                MaintenanceTicket.Status.IN_PROGRESS,
                MaintenanceTicket.Status.AWAITING_PARTS);

        List<Contract> contracts = contractRepository.findByOwnerId(ownerId, PageRequest.of(0, 5000)).getContent();
        long expiredContracts = contracts.stream().filter(contract -> isExpired(contract, today)).count();
        long expiringContracts = contracts.stream().filter(contract -> isExpiringSoon(contract, today)).count();

        List<AttentionItem> attentionItems = attentionItems(
                pendingInvoiceCount, pendingRevenue,
                overdueInvoiceCount, overdueRevenue,
                urgentTickets, openTickets, inProgressTickets, activeTickets,
                expiredContracts, expiringContracts,
                maintenance, locale);
        long pendingTasks = attentionItems.size();

        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageTitle", msg(locale, "admin.dashboard.title"));
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("monthBilled", monthBilled);
        model.addAttribute("monthOutstanding", monthOutstanding);
        model.addAttribute("monthOperatingCost", monthOperatingCost);
        model.addAttribute("monthTrendPercent", monthTrendPercent);
        model.addAttribute("monthTrendUp", monthTrendUp);
        model.addAttribute("dashboardFocusMonth", focusMonth);
        model.addAttribute("pendingRevenue", pendingRevenue);
        model.addAttribute("overdueRevenue", overdueRevenue);
        model.addAttribute("prevRevenue", prevRevenue);
        model.addAttribute("pendingInvoiceCount", pendingInvoiceCount);
        model.addAttribute("overdueInvoiceCount", overdueInvoiceCount);
        model.addAttribute("availableRooms", available);
        model.addAttribute("occupiedRooms", occupied);
        model.addAttribute("maintenanceRooms", maintenance);
        model.addAttribute("totalRooms", total);
        model.addAttribute("activeTenants", active);
        model.addAttribute("vacancyRate", vacancy);
        model.addAttribute("occupancyRate", occupancy);
        model.addAttribute("openTickets", openTickets);
        model.addAttribute("activeTickets", activeTickets);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("dashboardReport", dashboardReport);
        model.addAttribute("dashboardYear", dashboardYear);
        model.addAttribute("dashboardChartMidpoint",
                dashboardReport.getChartMax().divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP));
        model.addAttribute("electricCostRate", percentOf(dashboardReport.getOverview().getElectricCost(), dashboardReport.getOverview().getOperatingCost()));
        model.addAttribute("waterCostRate", percentOf(dashboardReport.getOverview().getWaterCost(), dashboardReport.getOverview().getOperatingCost()));
        model.addAttribute("maintenanceCostRate", percentOf(dashboardReport.getOverview().getMaintenanceCost(), dashboardReport.getOverview().getOperatingCost()));
        model.addAttribute("attentionItems", attentionItems);

        return "admin/dashboard";
    }

    private AdminReportService.MonthRow monthRow(List<AdminReportService.MonthRow> rows, int month) {
        if (rows == null) return null;
        return rows.stream()
                .filter(row -> row.getPeriod().getMonthValue() == month)
                .findFirst()
                .orElse(rows.isEmpty() ? null : rows.get(rows.size() - 1));
    }

    private BigDecimal sumInvoiceTotals(UUID ownerId, Invoice.InvoiceStatus... statuses) {
        return Arrays.stream(statuses)
                .map(status -> money(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, status)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long countInvoices(UUID ownerId, Invoice.InvoiceStatus... statuses) {
        return Arrays.stream(statuses)
                .mapToLong(status -> invoiceRepository.countByOwnerIdAndStatus(ownerId, status))
                .sum();
    }

    private long countTickets(UUID ownerId, MaintenanceTicket.Status... statuses) {
        return Arrays.stream(statuses)
                .mapToLong(status -> maintenanceTicketRepository.countByOwnerIdAndStatus(ownerId, status))
                .sum();
    }

    private BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        previous = money(previous);
        current = money(current);
        if (previous.compareTo(BigDecimal.ZERO) <= 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int percentOf(BigDecimal part, BigDecimal total) {
        total = money(total);
        if (total.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return money(part).multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static boolean isExpired(Contract contract, LocalDate today) {
        if (contract == null || contract.getStatus() == Contract.ContractStatus.TERMINATED) return false;
        if (contract.getStatus() == Contract.ContractStatus.EXPIRED) return true;
        return contract.getEndDate() != null && contract.getEndDate().isBefore(today);
    }

    private static boolean isExpiringSoon(Contract contract, LocalDate today) {
        if (contract == null || isExpired(contract, today)) return false;
        if (contract.getStatus() == Contract.ContractStatus.EXPIRING_SOON) return true;
        return contract.getEndDate() != null
                && !contract.getEndDate().isAfter(today.plusDays(30));
    }

    private List<AttentionItem> attentionItems(long pendingInvoiceCount,
                                               BigDecimal pendingRevenue,
                                               long overdueInvoiceCount,
                                               BigDecimal overdueRevenue,
                                               long urgentTickets,
                                               long openTickets,
                                               long inProgressTickets,
                                               long activeTickets,
                                               long expiredContracts,
                                               long expiringContracts,
                                               long maintenanceRooms,
                                               Locale locale) {
        List<AttentionItem> items = new ArrayList<>();
        if (overdueInvoiceCount > 0) {
            items.add(new AttentionItem(
                    "danger", "alert-triangle", msg(locale, "attention.finance"), msg(locale, "attention.overdueInvoices", overdueInvoiceCount),
                    "", msg(locale, "attention.invoiceCount", overdueInvoiceCount), MoneyFormatter.vnd(overdueRevenue),
                    "", "", "", "/admin/invoices?status=OVERDUE"));
        }
        if (pendingInvoiceCount > 0) {
            items.add(new AttentionItem(
                    "warning", "receipt", msg(locale, "attention.finance"), msg(locale, "attention.pendingInvoices", pendingInvoiceCount),
                    "", msg(locale, "attention.invoiceCount", pendingInvoiceCount), MoneyFormatter.vnd(pendingRevenue),
                    "", "", "", "/admin/invoices?status=PENDING"));
        }
        if (urgentTickets > 0) {
            items.add(new AttentionItem(
                    "danger", "siren", msg(locale, "attention.operation"), msg(locale, "attention.urgentTickets", urgentTickets),
                    "", msg(locale, "attention.ticketCount", urgentTickets), msg(locale, "attention.urgentLevel"),
                    "", "", "", "/admin/tickets?priority=URGENT"));
        } else if (openTickets > 0) {
            items.add(new AttentionItem(
                    "warning", "ticket-check", msg(locale, "attention.operation"), msg(locale, "attention.openTickets", openTickets),
                    "", msg(locale, "attention.ticketCount", openTickets), msg(locale, "attention.unaccepted"),
                    "", "", "", "/admin/tickets?status=OPEN"));
        } else if (inProgressTickets > 0 || activeTickets > 0) {
            items.add(new AttentionItem(
                    "info", "wrench", msg(locale, "attention.operation"), msg(locale, "attention.activeTickets", activeTickets),
                    "", msg(locale, "attention.ticketCount", activeTickets), msg(locale, "attention.inProgress"),
                    "", "", "", "/admin/tickets?status=IN_PROGRESS"));
        }
        if (expiredContracts > 0) {
            items.add(new AttentionItem(
                    "danger", "file-warning", msg(locale, "attention.contract"), msg(locale, "attention.expiredContracts", expiredContracts),
                    "", msg(locale, "attention.contractCount", expiredContracts), msg(locale, "attention.overdue"),
                    "", "", "", "/admin/contracts"));
        } else if (expiringContracts > 0) {
            items.add(new AttentionItem(
                    "warning", "file-clock", msg(locale, "attention.contract"), msg(locale, "attention.expiringContracts", expiringContracts),
                    "", msg(locale, "attention.contractCount", expiringContracts), msg(locale, "attention.expiringSoon"),
                    "", "", "", "/admin/contracts"));
        }
        if (maintenanceRooms > 0) {
            items.add(new AttentionItem(
                    "danger", "hard-hat", msg(locale, "attention.rooms"), msg(locale, "attention.maintenanceRooms", maintenanceRooms),
                    "", msg(locale, "attention.roomCount", maintenanceRooms), msg(locale, "attention.notRentable"),
                    "", "", "", "/admin/rooms?status=MAINTENANCE"));
        }
        return items;
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }

    public static class AttentionItem {
        private final String kind;
        private final String icon;
        private final String group;
        private final String title;
        private final String detail;
        private final String metric;
        private final String value;
        private final String priority;
        private final String status;
        private final String due;
        private final String href;

        public AttentionItem(String kind,
                             String icon,
                             String group,
                             String title,
                             String detail,
                             String metric,
                             String value,
                             String priority,
                             String status,
                             String due,
                             String href) {
            this.kind = kind;
            this.icon = icon;
            this.group = group;
            this.title = title;
            this.detail = detail;
            this.metric = metric;
            this.value = value;
            this.priority = priority;
            this.status = status;
            this.due = due;
            this.href = href;
        }

        public String getKind() { return kind; }
        public String getIcon() { return icon; }
        public String getGroup() { return group; }
        public String getTitle() { return title; }
        public String getDetail() { return detail; }
        public String getMetric() { return metric; }
        public String getValue() { return value; }
        public String getPriority() { return priority; }
        public String getStatus() { return status; }
        public String getDue() { return due; }
        public String getHref() { return href; }
    }
}
