package vn.glassliving.admin.page.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.report.service.AdminReportService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final AdminReportService reportService;

    @GetMapping
    public String reports(@AuthenticationPrincipal AppUserDetails me,
                          @RequestParam(required = false) Integer fromMonth,
                          @RequestParam(required = false) Integer fromYear,
                          @RequestParam(required = false) Integer toMonth,
                          @RequestParam(required = false) Integer toYear,
                          @RequestParam(required = false) UUID propertyId,
                          Model model) {
        AdminReportService.ReportData report = reportService.build(
                me.getId(), fromMonth, fromYear, toMonth, toYear, propertyId);

        model.addAttribute("activeNav", "reports");
        model.addAttribute("pageTitle", "Báo cáo");
        model.addAttribute("report", report);
        model.addAttribute("fromMonth", report.getFromPeriod().getMonthValue());
        model.addAttribute("fromYear", report.getFromPeriod().getYear());
        model.addAttribute("toMonth", report.getToPeriod().getMonthValue());
        model.addAttribute("toYear", report.getToPeriod().getYear());
        model.addAttribute("selectedPropertyId", report.getSelectedPropertyId());
        return "admin/reports";
    }

    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportCsv(@AuthenticationPrincipal AppUserDetails me,
                                            @RequestParam(required = false) Integer fromMonth,
                                            @RequestParam(required = false) Integer fromYear,
                                            @RequestParam(required = false) Integer toMonth,
                                            @RequestParam(required = false) Integer toYear,
                                            @RequestParam(required = false) UUID propertyId) {
        AdminReportService.ReportData report = reportService.build(
                me.getId(), fromMonth, fromYear, toMonth, toYear, propertyId);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Bao cao theo thang\n");
        csv.append("Ky,Doanh thu,Da thu,Con phai thu,Dien,Nuoc,Sua chua,Tong chi phi,Loi nhuan tam tinh,So hoa don\n");
        for (AdminReportService.MonthRow m : report.getMonths()) {
            csv.append(row(m.getLabel(), m.getRevenue(), m.getCollected(), m.getOutstanding(),
                    m.getElectricCost(), m.getWaterCost(), m.getMaintenanceCost(), m.getOperatingCost(),
                    m.getCollected().subtract(m.getOperatingCost()), BigDecimal.valueOf(m.getInvoiceCount())));
        }

        csv.append("\nBao cao theo co so\n");
        csv.append("Co so,So phong,Phong dang thue,Doanh thu,Da thu,Con phai thu,Dien,Nuoc,Sua chua,Tong chi phi,Loi nhuan tam tinh,So hoa don\n");
        for (AdminReportService.PropertyRow p : report.getPropertyRows()) {
            csv.append(row(p.getPropertyName(), BigDecimal.valueOf(p.getRoomCount()), BigDecimal.valueOf(p.getOccupiedRooms()),
                    p.getRevenue(), p.getCollected(), p.getOutstanding(), p.getElectricCost(), p.getWaterCost(),
                    p.getMaintenanceCost(), p.getOperatingCost(), p.getNet(), BigDecimal.valueOf(p.getInvoiceCount())));
        }

        csv.append("\nBao cao theo phong\n");
        csv.append("Phong,Ten phong,Co so,Doanh thu,Da thu,Con phai thu,Dien,Nuoc,Sua chua,Tong chi phi,Loi nhuan tam tinh,So hoa don\n");
        for (AdminReportService.RoomRow r : report.getRooms()) {
            csv.append(row(r.getRoomCode(), r.getRoomTitle(), r.getPropertyName(), r.getRevenue(), r.getCollected(),
                    r.getOutstanding(), r.getElectricCost(), r.getWaterCost(), r.getMaintenanceCost(),
                    r.getOperatingCost(), r.getNet(), BigDecimal.valueOf(r.getInvoiceCount())));
        }

        String filename = "smartrent-reports-" + report.getFromPeriod() + "-" + report.getToPeriod() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    private String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csv(values[i]));
        }
        return sb.append('\n').toString();
    }

    private String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
