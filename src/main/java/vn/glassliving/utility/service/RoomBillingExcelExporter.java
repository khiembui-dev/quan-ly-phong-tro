package vn.glassliving.utility.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class RoomBillingExcelExporter {

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] export(RoomBillingService.BillingDetail detail) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            buildInvoiceSheet(workbook, detail, styles);
            buildHistorySheet(workbook, detail, styles);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw BusinessException.badRequest("Không thể xuất file Excel. Vui lòng thử lại.");
        }
    }

    private void buildInvoiceSheet(Workbook workbook, RoomBillingService.BillingDetail detail, Styles styles) {
        RoomBillingService.BillingRow row = detail.getRow();
        Sheet sheet = workbook.createSheet("Hóa đơn phòng");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 12);
        setWidths(sheet, 8, 28, 14, 14, 14, 16, 18, 34);

        int r = 0;
        Row title = sheet.createRow(r++);
        title.setHeightInPoints(30);
        cell(title, 0, "BẢNG TÍNH TIỀN PHÒNG", styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        Row subtitle = sheet.createRow(r++);
        cell(subtitle, 0, "Kỳ " + detail.getMonth() + "/" + detail.getYear()
                + " · Xuất ngày " + VN_DATE.format(LocalDate.now()), styles.muted);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));
        r++;

        r = infoRow(sheet, r, "Mã phòng", row.getRoom().getCode(), "Tên phòng", row.getRoom().getTitle(), styles);
        r = infoRow(sheet, r, "Cơ sở", row.getProperty().getName(), "Trạng thái", row.getStatusLabel(), styles);
        r = infoRow(sheet, r, "Khách thuê", row.getTenant() != null ? row.getTenant().getFullName() : "Phòng trống",
                "Liên hệ", tenantContact(row), styles);
        r = infoRow(sheet, r, "Mã hóa đơn", row.getInvoice() != null ? row.getInvoice().getCode() : "Chưa phát hành",
                "Hạn thanh toán", row.getInvoice() != null && row.getInvoice().getDueDate() != null
                        ? VN_DATE.format(row.getInvoice().getDueDate()) : "-", styles);
        r++;

        Row header = sheet.createRow(r++);
        String[] columns = {"STT", "Hạng mục", "Chỉ số cũ", "Chỉ số mới", "Số lượng", "Đơn giá", "Thành tiền", "Ghi chú"};
        for (int i = 0; i < columns.length; i++) {
            cell(header, i, columns[i], styles.tableHeader);
        }

        int firstAmountRow = r + 1;
        int index = 1;
        r = line(sheet, r, index++, "Tiền phòng", null, null, BigDecimal.ONE,
                row.getRentAmount(), row.getRentAmount(), "Tiền thuê tháng", styles);

        for (RoomBillingService.FeeLine fee : row.getFeeLines()) {
            r = line(sheet, r, index++, fee.getName(), null, null, BigDecimal.ONE,
                    fee.getAmount(), fee.getAmount(), "Phí cố định", styles);
        }

        r = line(sheet, r, index++, "Điện", row.getElectricPrev(), row.getElectricCurr(),
                row.getElectricUsage(), row.getElectricUnitPrice(), row.getElectricAmount(), "kWh", styles);
        r = line(sheet, r, index++, "Nước", row.getWaterPrev(), row.getWaterCurr(),
                row.getWaterUsage(), row.getWaterUnitPrice(), row.getWaterAmount(), "m³", styles);

        int lastAmountRow = r;
        Row total = sheet.createRow(r++);
        cell(total, 0, "", styles.total);
        cell(total, 1, "TỔNG THANH TOÁN", styles.total);
        for (int c = 2; c <= 5; c++) {
            cell(total, c, "", styles.total);
        }
        Cell totalCell = cell(total, 6, "", styles.totalMoney);
        totalCell.setCellFormula("SUM(G" + firstAmountRow + ":G" + lastAmountRow + ")");
        cell(total, 7, row.getInvoice() != null ? invoiceStatus(row.getInvoice()) : "Chưa phát hành hóa đơn", styles.total);
        r += 2;

        Row noteTitle = sheet.createRow(r++);
        cell(noteTitle, 0, "Ghi chú kiểm tra", styles.section);
        sheet.addMergedRegion(new CellRangeAddress(noteTitle.getRowNum(), noteTitle.getRowNum(), 0, 7));

        Row note = sheet.createRow(r++);
        note.setHeightInPoints(42);
        String readingNote = row.getReading() != null ? row.getReading().getNote() : null;
        cell(note, 0, readingNote == null || readingNote.isBlank() ? "Không có ghi chú nội bộ." : readingNote, styles.note);
        sheet.addMergedRegion(new CellRangeAddress(note.getRowNum(), note.getRowNum(), 0, 7));

        addTableFilter(sheet, header.getRowNum(), total.getRowNum() - 1, 0, 7);
    }

    private void buildHistorySheet(Workbook workbook, RoomBillingService.BillingDetail detail, Styles styles) {
        Sheet sheet = workbook.createSheet("Lịch sử hóa đơn");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 3);
        setWidths(sheet, 10, 18, 26, 13, 13, 12, 16, 13, 13, 12, 16, 26, 16, 16, 16);

        int r = 0;
        Row title = sheet.createRow(r++);
        title.setHeightInPoints(28);
        cell(title, 0, "LỊCH SỬ TÍNH TIỀN THEO PHÒNG", styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 14));
        Row subtitle = sheet.createRow(r++);
        cell(subtitle, 0, detail.getRow().getRoom().getCode() + " · " + detail.getRow().getRoom().getTitle(), styles.muted);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 14));

        Row header = sheet.createRow(r++);
        String[] columns = {"Kỳ", "Mã hóa đơn", "Người thuê", "Điện cũ", "Điện mới", "kWh", "Tiền điện",
                "Nước cũ", "Nước mới", "m³", "Tiền nước", "Phí chi tiết", "Phí khác", "Tổng tiền", "Trạng thái"};
        for (int i = 0; i < columns.length; i++) {
            cell(header, i, columns[i], styles.tableHeader);
        }

        for (RoomBillingService.InvoiceHistoryRow item : detail.getHistory()) {
            Invoice inv = item.getInvoice();
            Row row = sheet.createRow(r++);
            cell(row, 0, inv.getPeriodMonth() + "/" + inv.getPeriodYear(), styles.body);
            cell(row, 1, inv.getCode(), styles.bodyMono);
            cell(row, 2, item.getTenant() != null ? item.getTenant().getFullName() : "Khách thuê", styles.body);
            number(row, 3, inv.getElectricPrev(), styles.quantity);
            number(row, 4, inv.getElectricCurr(), styles.quantity);
            number(row, 5, usage(inv.getElectricPrev(), inv.getElectricCurr()), styles.quantity);
            money(row, 6, inv.getElectricAmount(), styles.money);
            number(row, 7, inv.getWaterPrev(), styles.quantity);
            number(row, 8, inv.getWaterCurr(), styles.quantity);
            number(row, 9, usage(inv.getWaterPrev(), inv.getWaterCurr()), styles.quantity);
            money(row, 10, inv.getWaterAmount(), styles.money);
            cell(row, 11, feeBreakdown(inv, item.getOtherItems()), styles.bodyWrap);
            money(row, 12, nz(inv.getServiceAmount()).add(nz(inv.getOtherAmount())), styles.money);
            money(row, 13, inv.getTotalAmount(), styles.moneyBold);
            cell(row, 14, invoiceStatus(inv), styles.status);
        }

        if (detail.getHistory().isEmpty()) {
            Row empty = sheet.createRow(r++);
            cell(empty, 0, "Chưa có lịch sử hóa đơn cho phòng này.", styles.note);
            sheet.addMergedRegion(new CellRangeAddress(empty.getRowNum(), empty.getRowNum(), 0, 14));
        } else {
            Row total = sheet.createRow(r++);
            cell(total, 0, "Tổng lịch sử", styles.total);
            sheet.addMergedRegion(new CellRangeAddress(total.getRowNum(), total.getRowNum(), 0, 12));
            Cell totalCell = cell(total, 13, "", styles.totalMoney);
            totalCell.setCellFormula("SUM(N4:N" + (r - 1) + ")");
            cell(total, 14, "", styles.total);
            addTableFilter(sheet, header.getRowNum(), total.getRowNum() - 1, 0, 14);
        }
    }

    private int infoRow(Sheet sheet, int r, String leftLabel, String leftValue,
                        String rightLabel, String rightValue, Styles styles) {
        Row row = sheet.createRow(r);
        cell(row, 0, leftLabel, styles.infoLabel);
        cell(row, 1, leftValue, styles.infoValue);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 1, 3));
        cell(row, 4, rightLabel, styles.infoLabel);
        cell(row, 5, rightValue, styles.infoValue);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 5, 7));
        return r + 1;
    }

    private int line(Sheet sheet, int r, int index, String name,
                     BigDecimal previous, BigDecimal current, BigDecimal quantity,
                     BigDecimal unitPrice, BigDecimal amount, String note, Styles styles) {
        Row row = sheet.createRow(r);
        number(row, 0, BigDecimal.valueOf(index), styles.bodyCenter);
        cell(row, 1, name, styles.body);
        number(row, 2, previous, styles.quantity);
        number(row, 3, current, styles.quantity);
        number(row, 4, quantity, styles.quantity);
        money(row, 5, unitPrice, styles.money);
        money(row, 6, amount, styles.moneyBold);
        cell(row, 7, note, styles.bodyWrap);
        return r + 1;
    }

    private static String tenantContact(RoomBillingService.BillingRow row) {
        if (row.getTenant() == null) return "-";
        String email = Objects.toString(row.getTenant().getEmail(), "");
        String phone = Objects.toString(row.getTenant().getPhone(), "");
        if (!phone.isBlank()) return email + " · " + phone;
        return email;
    }

    private static String feeBreakdown(Invoice invoice, List<InvoiceLineItem> otherItems) {
        StringBuilder text = new StringBuilder();
        if (nz(invoice.getServiceAmount()).signum() > 0) {
            text.append("Dịch vụ: ").append(invoice.getServiceAmount().toPlainString()).append("đ");
        }
        for (InvoiceLineItem item : otherItems) {
            if (text.length() > 0) text.append("\n");
            text.append(item.getName()).append(": ").append(nz(item.getAmount()).toPlainString()).append("đ");
        }
        return text.length() == 0 ? "-" : text.toString();
    }

    private static String invoiceStatus(Invoice invoice) {
        if (invoice == null || invoice.getStatus() == null) return "-";
        return switch (invoice.getStatus()) {
            case PAID -> "Đã thanh toán";
            case PENDING -> "Chờ thanh toán";
            case PARTIALLY_PAID -> "Thanh toán một phần";
            case OVERDUE -> "Quá hạn";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static void setWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private static void addTableFilter(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        if (lastRow > firstRow) {
            sheet.setAutoFilter(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
        }
    }

    private static Cell cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
        return cell;
    }

    private static void money(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static BigDecimal usage(BigDecimal previous, BigDecimal current) {
        BigDecimal value = nz(current).subtract(nz(previous));
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle muted;
        final CellStyle section;
        final CellStyle tableHeader;
        final CellStyle infoLabel;
        final CellStyle infoValue;
        final CellStyle body;
        final CellStyle bodyMono;
        final CellStyle bodyCenter;
        final CellStyle bodyWrap;
        final CellStyle money;
        final CellStyle moneyBold;
        final CellStyle quantity;
        final CellStyle status;
        final CellStyle total;
        final CellStyle totalMoney;
        final CellStyle note;

        Styles(Workbook workbook) {
            DataFormat format = workbook.createDataFormat();
            Font titleFont = font(workbook, 18, true, IndexedColors.WHITE);
            Font headerFont = font(workbook, 11, true, IndexedColors.WHITE);
            Font boldFont = font(workbook, 11, true, IndexedColors.DARK_BLUE);
            Font normalFont = font(workbook, 11, false, IndexedColors.DARK_BLUE);
            Font mutedFont = font(workbook, 10, false, IndexedColors.GREY_50_PERCENT);
            Font totalFont = font(workbook, 12, true, IndexedColors.WHITE);
            Font monoFont = font(workbook, 10, true, IndexedColors.INDIGO);
            monoFont.setFontName("Consolas");

            title = style(workbook, titleFont, IndexedColors.INDIGO, true, HorizontalAlignment.CENTER);
            muted = style(workbook, mutedFont, IndexedColors.WHITE, false, HorizontalAlignment.LEFT);
            section = style(workbook, boldFont, IndexedColors.LIGHT_CORNFLOWER_BLUE, true, HorizontalAlignment.LEFT);
            tableHeader = style(workbook, headerFont, IndexedColors.INDIGO, true, HorizontalAlignment.CENTER);
            infoLabel = style(workbook, boldFont, IndexedColors.PALE_BLUE, true, HorizontalAlignment.LEFT);
            infoValue = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.LEFT);
            body = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.LEFT);
            bodyMono = style(workbook, monoFont, IndexedColors.WHITE, true, HorizontalAlignment.LEFT);
            bodyCenter = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.CENTER);
            bodyWrap = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.LEFT);
            bodyWrap.setWrapText(true);
            money = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.RIGHT);
            money.setDataFormat(format.getFormat("#,##0 \"đ\""));
            moneyBold = style(workbook, boldFont, IndexedColors.WHITE, true, HorizontalAlignment.RIGHT);
            moneyBold.setDataFormat(format.getFormat("#,##0 \"đ\""));
            quantity = style(workbook, normalFont, IndexedColors.WHITE, true, HorizontalAlignment.RIGHT);
            quantity.setDataFormat(format.getFormat("#,##0.00"));
            status = style(workbook, boldFont, IndexedColors.LIGHT_YELLOW, true, HorizontalAlignment.CENTER);
            total = style(workbook, totalFont, IndexedColors.INDIGO, true, HorizontalAlignment.LEFT);
            totalMoney = style(workbook, totalFont, IndexedColors.INDIGO, true, HorizontalAlignment.RIGHT);
            totalMoney.setDataFormat(format.getFormat("#,##0 \"đ\""));
            note = style(workbook, normalFont, IndexedColors.LEMON_CHIFFON, true, HorizontalAlignment.LEFT);
            note.setWrapText(true);
        }

        private static Font font(Workbook workbook, int size, boolean bold, IndexedColors color) {
            Font font = workbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(color.getIndex());
            return font;
        }

        private static CellStyle style(Workbook workbook, Font font, IndexedColors fill,
                                       boolean border, HorizontalAlignment alignment) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            if (border) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
                style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            }
            return style;
        }
    }
}
