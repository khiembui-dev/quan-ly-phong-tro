package vn.glassliving.admin.page.utilities;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.service.RoomBillingExcelExporter;
import vn.glassliving.utility.service.RoomBillingService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/utilities")
@RequiredArgsConstructor
public class UtilitiesController {

    private final RoomBillingService billingService;
    private final InvoiceService invoiceService;
    private final RoomBillingExcelExporter excelExporter;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails me,
                       @RequestParam(required = false) UUID propertyId,
                       @RequestParam(required = false) Short year,
                       @RequestParam(required = false) Short month,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "all") String status,
                       Model model) {
        YearMonth current = YearMonth.now();
        short periodYear = year != null ? year : (short) current.getYear();
        short periodMonth = month != null ? month : (short) current.getMonthValue();

        RoomBillingService.BillingView view = billingService.buildView(
                me.getId(), periodYear, periodMonth, propertyId, q, status);

        model.addAttribute("activeNav", "utilities");
        model.addAttribute("pageTitle", "Tính tiền phòng");
        model.addAttribute("billingView", view);
        model.addAttribute("rows", view.getRows());
        model.addAttribute("summary", view.getSummary());
        model.addAttribute("properties", view.getProperties());
        model.addAttribute("currentPropertyId", propertyId);
        model.addAttribute("filterQ", q);
        model.addAttribute("filterStatus", status);
        model.addAttribute("periodYear", periodYear);
        model.addAttribute("periodMonth", periodMonth);
        model.addAttribute("today", LocalDate.now());
        return "admin/utilities";
    }

    @GetMapping("/rooms/{roomId}")
    public String detail(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID roomId,
                         @RequestParam(required = false) Short year,
                         @RequestParam(required = false) Short month,
                         Model model) {
        YearMonth current = YearMonth.now();
        short periodYear = year != null ? year : (short) current.getYear();
        short periodMonth = month != null ? month : (short) current.getMonthValue();
        YearMonth selected = YearMonth.of(periodYear, periodMonth);
        RoomBillingService.BillingDetail detail = billingService.buildDetail(me.getId(), roomId, periodYear, periodMonth);

        model.addAttribute("activeNav", "utilities");
        model.addAttribute("pageTitle", "Tính tiền phòng");
        model.addAttribute("detail", detail);
        model.addAttribute("row", detail.getRow());
        model.addAttribute("history", detail.getHistory());
        model.addAttribute("otherItems", detail.getOtherItems());
        model.addAttribute("periodYear", periodYear);
        model.addAttribute("periodMonth", periodMonth);
        model.addAttribute("prevPeriodYear", selected.minusMonths(1).getYear());
        model.addAttribute("prevPeriodMonth", selected.minusMonths(1).getMonthValue());
        model.addAttribute("nextPeriodYear", selected.plusMonths(1).getYear());
        model.addAttribute("nextPeriodMonth", selected.plusMonths(1).getMonthValue());
        model.addAttribute("periodYears", periodYears(periodYear));
        model.addAttribute("today", LocalDate.now());
        return "admin/utility-room-detail";
    }

    @PostMapping("/readings")
    public String saveReading(@AuthenticationPrincipal AppUserDetails me,
                              @RequestParam UUID roomId,
                              @RequestParam short year,
                              @RequestParam short month,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readingDate,
                              @RequestParam(required = false) BigDecimal electricPrev,
                              @RequestParam(required = false) BigDecimal electricCurr,
                              @RequestParam(required = false) BigDecimal waterPrev,
                              @RequestParam(required = false) BigDecimal waterCurr,
                              @RequestParam(required = false) String note,
                              @RequestParam(required = false) UUID propertyId,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false, defaultValue = "all") String status,
                              RedirectAttributes ra) {
        try {
            UtilityReading reading = billingService.saveReading(me.getId(), roomId, year, month,
                    readingDate, electricPrev, electricCurr, waterPrev, waterCurr, note);
            FlashAlert.ok(ra, "Đã lưu chỉ số phòng cho kỳ " + reading.getPeriodMonth() + "/" + reading.getPeriodYear() + ".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        addReturnParams(ra, year, month, propertyId, q, status);
        return "redirect:/admin/utilities";
    }

    @PostMapping("/rooms/{roomId}/readings")
    public String saveRoomReading(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID roomId,
                                  @RequestParam short year,
                                  @RequestParam short month,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readingDate,
                                  @RequestParam(required = false) BigDecimal electricPrev,
                                  @RequestParam(required = false) BigDecimal electricCurr,
                                  @RequestParam(required = false) BigDecimal waterPrev,
                                  @RequestParam(required = false) BigDecimal waterCurr,
                                  @RequestParam(required = false) String note,
                                  RedirectAttributes ra) {
        try {
            UtilityReading reading = billingService.saveReading(me.getId(), roomId, year, month,
                    readingDate, electricPrev, electricCurr, waterPrev, waterCurr, note);
            FlashAlert.ok(ra, "Đã lưu chỉ số kỳ " + reading.getPeriodMonth() + "/" + reading.getPeriodYear() + ".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectRoom(roomId, year, month);
    }

    @PostMapping("/rooms/{roomId}/issue")
    public String saveAndIssueInvoice(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID roomId,
                                      @RequestParam short year,
                                      @RequestParam short month,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readingDate,
                                      @RequestParam(required = false) BigDecimal electricPrev,
                                      @RequestParam(required = false) BigDecimal electricCurr,
                                      @RequestParam(required = false) BigDecimal waterPrev,
                                      @RequestParam(required = false) BigDecimal waterCurr,
                                      @RequestParam(required = false) String note,
                                      RedirectAttributes ra) {
        try {
            Invoice invoice = billingService.saveReadingAndIssue(me.getId(), roomId, year, month,
                    readingDate, electricPrev, electricCurr, waterPrev, waterCurr, note);
            FlashAlert.ok(ra, "Đã lưu chỉ số và đồng bộ hóa đơn " + invoice.getCode() + " cho tài khoản khách thuê.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectRoom(roomId, year, month);
    }

    @PostMapping("/period/lock")
    public String lockPeriod(@AuthenticationPrincipal AppUserDetails me,
                             @RequestParam short year,
                             @RequestParam short month,
                             @RequestParam(required = false) UUID propertyId,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false, defaultValue = "all") String status,
                             RedirectAttributes ra) {
        try {
            int locked = billingService.lockPeriod(me.getId(), year, month, propertyId);
            FlashAlert.ok(ra, "Đã chốt kỳ " + month + "/" + year + " với " + locked + " bản ghi được khóa.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        addReturnParams(ra, year, month, propertyId, q, status);
        return "redirect:/admin/utilities";
    }

    @PostMapping("/invoices/{invoiceId}/status")
    public String updateInvoiceStatus(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID invoiceId,
                                      @RequestParam Invoice.InvoiceStatus status,
                                      @RequestParam UUID roomId,
                                      @RequestParam short year,
                                      @RequestParam short month,
                                      RedirectAttributes ra) {
        try {
            Invoice invoice;
            if (status == Invoice.InvoiceStatus.PAID) {
                invoice = invoiceService.markPaid(me.getId(), invoiceId);
                FlashAlert.ok(ra, "Đã đánh dấu hóa đơn " + invoice.getCode() + " là đã thanh toán.");
            } else if (status == Invoice.InvoiceStatus.PENDING) {
                invoice = invoiceService.markPending(me.getId(), invoiceId);
                FlashAlert.ok(ra, "Đã chuyển hóa đơn " + invoice.getCode() + " về trạng thái chờ thanh toán.");
            } else {
                throw BusinessException.badRequest("Trạng thái không được hỗ trợ ở màn hình này.");
            }
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectRoom(roomId, year, month);
    }

    @PostMapping("/invoices")
    public String createInvoice(@AuthenticationPrincipal AppUserDetails me,
                                @RequestParam UUID roomId,
                                @RequestParam short year,
                                @RequestParam short month,
                                @RequestParam(required = false) UUID propertyId,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false, defaultValue = "all") String status,
                                RedirectAttributes ra) {
        try {
            Invoice invoice = billingService.createInvoice(me.getId(), roomId, year, month);
            FlashAlert.ok(ra, "Đã tạo hóa đơn " + invoice.getCode() + " cho kỳ " + month + "/" + year + ".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        addReturnParams(ra, year, month, propertyId, q, status);
        return "redirect:/admin/utilities";
    }

    @GetMapping("/rooms/{roomId}/export.xlsx")
    public ResponseEntity<byte[]> exportRoomExcel(@AuthenticationPrincipal AppUserDetails me,
                                                  @PathVariable UUID roomId,
                                                  @RequestParam(required = false) Short year,
                                                  @RequestParam(required = false) Short month) {
        YearMonth current = YearMonth.now();
        short periodYear = year != null ? year : (short) current.getYear();
        short periodMonth = month != null ? month : (short) current.getMonthValue();
        RoomBillingService.BillingDetail detail = billingService.buildDetail(me.getId(), roomId, periodYear, periodMonth);
        byte[] workbook = excelExporter.export(detail);

        String filename = "hoa-don-" + safeFilePart(detail.getRow().getRoom().getCode())
                + "-" + periodYear + "-" + String.format("%02d", periodMonth) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(workbook.length)
                .body(workbook);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AppUserDetails me,
                                            @RequestParam(required = false) UUID propertyId,
                                            @RequestParam(required = false) Short year,
                                            @RequestParam(required = false) Short month,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false, defaultValue = "all") String status) {
        YearMonth current = YearMonth.now();
        short periodYear = year != null ? year : (short) current.getYear();
        short periodMonth = month != null ? month : (short) current.getMonthValue();
        RoomBillingService.BillingView view = billingService.buildView(
                me.getId(), periodYear, periodMonth, propertyId, q, status);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Ky,Phong,Khach,Co so,Tien phong,Phi co dinh,Dien cu,Dien moi,kWh,Tien dien,Nuoc cu,Nuoc moi,m3,Tien nuoc,Tong,Trang thai\n");
        for (RoomBillingService.BillingRow row : view.getRows()) {
            csv.append(csv(periodMonth + "/" + periodYear)).append(',')
                    .append(csv(row.getRoom().getCode() + " - " + row.getRoom().getTitle())).append(',')
                    .append(csv(row.getTenant() != null ? row.getTenant().getFullName() : "Phong trong")).append(',')
                    .append(csv(row.getProperty().getName())).append(',')
                    .append(row.getRentAmount()).append(',')
                    .append(row.getFixedFeeTotal()).append(',')
                    .append(row.getElectricPrev()).append(',')
                    .append(row.getElectricCurr()).append(',')
                    .append(row.getElectricUsage()).append(',')
                    .append(row.getElectricAmount()).append(',')
                    .append(row.getWaterPrev()).append(',')
                    .append(row.getWaterCurr()).append(',')
                    .append(row.getWaterUsage()).append(',')
                    .append(row.getWaterAmount()).append(',')
                    .append(row.getTotalAmount()).append(',')
                    .append(csv(row.getStatusLabel()))
                    .append('\n');
        }

        String filename = "tinh-tien-phong-" + periodYear + "-" + String.format("%02d", periodMonth) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void addReturnParams(RedirectAttributes ra, short year, short month,
                                        UUID propertyId, String q, String status) {
        ra.addAttribute("year", year);
        ra.addAttribute("month", month);
        if (propertyId != null) ra.addAttribute("propertyId", propertyId);
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        if (status != null && !status.isBlank() && !"all".equals(status)) ra.addAttribute("status", status);
    }

    private static String redirectRoom(UUID roomId, short year, short month) {
        return "redirect:/admin/utilities/rooms/" + roomId + "?year=" + year + "&month=" + month;
    }

    private static List<Integer> periodYears(short selectedYear) {
        int start = Math.min(LocalDate.now().getYear() - 2, selectedYear - 2);
        int end = Math.max(LocalDate.now().getYear() + 3, selectedYear + 3);
        List<Integer> years = new ArrayList<>();
        for (int y = start; y <= end; y++) {
            years.add(y);
        }
        return years;
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String safeFilePart(String value) {
        String safe = value == null ? "phong" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "phong" : safe;
    }
}
