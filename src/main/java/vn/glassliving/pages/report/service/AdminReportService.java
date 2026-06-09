package vn.glassliving.report.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InvoiceRepository invoiceRepository;
    private final UtilityReadingRepository utilityRepository;
    private final MaintenanceTicketRepository maintenanceRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    public ReportData build(UUID ownerId,
                            Integer fromMonth,
                            Integer fromYear,
                            Integer toMonth,
                            Integer toYear,
                            UUID requestedPropertyId) {
        YearMonth now = YearMonth.now();
        YearMonth to = validPeriod(toYear, toMonth, now);
        YearMonth from = validPeriod(fromYear, fromMonth, to.minusMonths(5));
        if (from.isAfter(to)) {
            YearMonth tmp = from;
            from = to;
            to = tmp;
        }
        if (from.until(to, java.time.temporal.ChronoUnit.MONTHS) > 23) {
            from = to.minusMonths(23);
        }

        List<Property> properties = propertyRepository.findByOwnerIdOrderByNameAsc(ownerId);
        Map<UUID, Property> propertyById = new HashMap<>();
        for (Property p : properties) propertyById.put(p.getId(), p);

        UUID selectedPropertyId = propertyById.containsKey(requestedPropertyId) ? requestedPropertyId : null;

        List<Room> rooms = roomRepository.findByOwnerId(ownerId,
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.ASC, "code"))).getContent();
        Map<UUID, Room> roomById = new HashMap<>();
        for (Room r : rooms) roomById.put(r.getId(), r);

        List<Room> visibleRooms = rooms.stream()
                .filter(r -> selectedPropertyId == null || Objects.equals(r.getPropertyId(), selectedPropertyId))
                .toList();

        LinkedHashMap<YearMonth, MonthAccumulator> monthMap = new LinkedHashMap<>();
        for (YearMonth cursor = from; !cursor.isAfter(to); cursor = cursor.plusMonths(1)) {
            monthMap.put(cursor, new MonthAccumulator(cursor));
        }

        Map<UUID, RoomAccumulator> roomStats = new LinkedHashMap<>();
        for (Room r : visibleRooms) {
            Property property = propertyById.get(r.getPropertyId());
            roomStats.put(r.getId(), new RoomAccumulator(r, property));
        }

        Map<UUID, PropertyAccumulator> propertyStats = new LinkedHashMap<>();
        for (Property p : properties) {
            if (selectedPropertyId == null || Objects.equals(p.getId(), selectedPropertyId)) {
                propertyStats.put(p.getId(), new PropertyAccumulator(p));
            }
        }
        for (Room r : visibleRooms) {
            PropertyAccumulator acc = propertyStats.get(r.getPropertyId());
            if (acc != null) {
                acc.roomCount++;
                if (r.getStatus() == Room.RoomStatus.OCCUPIED || r.getCurrentTenantId() != null) acc.occupiedRooms++;
            }
        }

        List<Invoice> invoices = invoiceRepository.findForReport(
                ownerId, (short) from.getYear(), (short) from.getMonthValue(),
                (short) to.getYear(), (short) to.getMonthValue());
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) continue;
            Room room = roomById.get(invoice.getRoomId());
            if (!matchesProperty(room, selectedPropertyId)) continue;

            YearMonth period = YearMonth.of(invoice.getPeriodYear(), invoice.getPeriodMonth());
            MonthAccumulator month = monthMap.get(period);
            BigDecimal billed = money(invoice.getTotalAmount());
            BigDecimal collected = collectedAmount(invoice);
            BigDecimal outstanding = billed.subtract(collected).max(ZERO);

            if (month != null) month.addInvoice(billed, collected, outstanding);

            RoomAccumulator roomAcc = roomStats.computeIfAbsent(invoice.getRoomId(),
                    id -> new RoomAccumulator(room, room != null ? propertyById.get(room.getPropertyId()) : null));
            roomAcc.addInvoice(billed, collected, outstanding);

            UUID propertyId = room != null ? room.getPropertyId() : null;
            PropertyAccumulator propertyAcc = propertyStats.get(propertyId);
            if (propertyAcc != null) propertyAcc.addInvoice(billed, collected, outstanding);
        }

        List<UtilityReading> readings = utilityRepository.findForReport(
                ownerId, (short) from.getYear(), (short) from.getMonthValue(),
                (short) to.getYear(), (short) to.getMonthValue());
        for (UtilityReading reading : readings) {
            Room room = roomById.get(reading.getRoomId());
            if (!matchesProperty(room, selectedPropertyId)) continue;

            YearMonth period = YearMonth.of(reading.getPeriodYear(), reading.getPeriodMonth());
            MonthAccumulator month = monthMap.get(period);
            BigDecimal electric = money(reading.getElectricAmount());
            BigDecimal water = money(reading.getWaterAmount());

            if (month != null) month.addUtility(electric, water);

            RoomAccumulator roomAcc = roomStats.computeIfAbsent(reading.getRoomId(),
                    id -> new RoomAccumulator(room, room != null ? propertyById.get(room.getPropertyId()) : null));
            roomAcc.addUtility(electric, water);

            UUID propertyId = room != null ? room.getPropertyId() : reading.getPropertyId();
            PropertyAccumulator propertyAcc = propertyStats.get(propertyId);
            if (propertyAcc != null) propertyAcc.addUtility(electric, water);
        }

        var zone = ZoneId.systemDefault();
        var reportFrom = from.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        var reportTo = to.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        List<MaintenanceTicket> tickets = maintenanceRepository.findForReport(ownerId, reportFrom, reportTo);
        for (MaintenanceTicket ticket : tickets) {
            if (ticket.getStatus() == MaintenanceTicket.Status.CANCELLED) continue;

            Room room = ticket.getRoomId() != null ? roomById.get(ticket.getRoomId()) : null;
            UUID ticketPropertyId = room != null ? room.getPropertyId() : ticket.getPropertyId();
            if (selectedPropertyId != null && !Objects.equals(ticketPropertyId, selectedPropertyId)) continue;

            BigDecimal cost = maintenanceCost(ticket);
            YearMonth period = YearMonth.from(ticket.getReportedAt().toLocalDate());
            MonthAccumulator month = monthMap.get(period);
            if (month != null) month.addMaintenance(cost);

            if (room != null) {
                RoomAccumulator roomAcc = roomStats.computeIfAbsent(room.getId(),
                        id -> new RoomAccumulator(room, propertyById.get(room.getPropertyId())));
                roomAcc.addMaintenance(cost);
            }

            PropertyAccumulator propertyAcc = propertyStats.get(ticketPropertyId);
            if (propertyAcc != null) propertyAcc.addMaintenance(cost);
        }

        List<BigDecimal> revenueSeries = monthMap.values().stream()
                .map(m -> m.revenue)
                .toList();

        List<MonthRow> monthRows = new ArrayList<>();
        BigDecimal chartMax = ZERO;
        int index = 0;
        int count = monthMap.size();
        for (MonthAccumulator acc : monthMap.values()) {
            BigDecimal avg3 = average(revenueSeries, index, 3);
            BigDecimal avg6 = average(revenueSeries, index, 6);
            chartMax = max(chartMax, acc.revenue, avg3, avg6, acc.operatingCost());
            monthRows.add(acc.toRow(avg3, avg6, index, count));
            index++;
        }
        if (chartMax.compareTo(ZERO) <= 0) chartMax = BigDecimal.ONE;

        List<MonthRow> scaledMonths = new ArrayList<>();
        for (MonthRow row : monthRows) {
            scaledMonths.add(row.withScale(chartMax));
        }
        monthRows = scaledMonths;

        List<RoomRow> roomRows = roomStats.values().stream()
                .map(RoomAccumulator::toRow)
                .sorted(Comparator.comparing(RoomRow::getCollected).reversed()
                        .thenComparing(RoomRow::getRoomCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<PropertyRow> propertyRows = propertyStats.values().stream()
                .map(PropertyAccumulator::toRow)
                .sorted(Comparator.comparing(PropertyRow::getCollected).reversed()
                        .thenComparing(PropertyRow::getPropertyName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        int tenantCount = (int) visibleRooms.stream()
                .map(Room::getCurrentTenantId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        int vacantRooms = (int) visibleRooms.stream()
                .filter(r -> r.getCurrentTenantId() == null && r.getStatus() == Room.RoomStatus.AVAILABLE)
                .count();
        int maintenanceRooms = (int) visibleRooms.stream()
                .filter(r -> r.getStatus() == Room.RoomStatus.MAINTENANCE)
                .count();

        Overview overview = buildOverview(monthRows, roomRows, propertyRows,
                visibleRooms.size(), tenantCount, vacantRooms, maintenanceRooms);
        List<String> insights = buildInsights(overview, monthRows, roomRows, propertyRows);

        return new ReportData(
                properties,
                selectedPropertyId,
                from,
                to,
                overview,
                monthRows,
                roomRows,
                propertyRows,
                insights,
                points(monthRows, chartMax, "revenue"),
                points(monthRows, chartMax, "avg3"),
                points(monthRows, chartMax, "avg6"),
                points(monthRows, chartMax, "operating"),
                areaPath(monthRows, chartMax, MonthRow::getRevenue),
                chartMax
        );
    }

    private Overview buildOverview(List<MonthRow> monthRows,
                                   List<RoomRow> roomRows,
                                   List<PropertyRow> propertyRows,
                                   int visibleRoomCount,
                                   int tenantCount,
                                   int vacantRooms,
                                   int maintenanceRooms) {
        BigDecimal revenue = sumMonths(monthRows, MonthRow::getRevenue);
        BigDecimal collected = sumMonths(monthRows, MonthRow::getCollected);
        BigDecimal outstanding = sumMonths(monthRows, MonthRow::getOutstanding);
        BigDecimal electric = sumMonths(monthRows, MonthRow::getElectricCost);
        BigDecimal water = sumMonths(monthRows, MonthRow::getWaterCost);
        BigDecimal maintenance = sumMonths(monthRows, MonthRow::getMaintenanceCost);
        BigDecimal operating = electric.add(water).add(maintenance);
        BigDecimal net = collected.subtract(operating);
        int invoiceCount = monthRows.stream().mapToInt(MonthRow::getInvoiceCount).sum();
        int occupiedRooms = propertyRows.stream().mapToInt(PropertyRow::getOccupiedRooms).sum();

        MonthRow last = monthRows.isEmpty() ? null : monthRows.get(monthRows.size() - 1);
        MonthRow prev = monthRows.size() >= 2 ? monthRows.get(monthRows.size() - 2) : null;
        BigDecimal trend = prev == null ? ZERO : percentChange(prev.getCollected(), last.getCollected());
        BigDecimal roomTotal = BigDecimal.valueOf(visibleRoomCount);

        return new Overview(
                revenue,
                collected,
                outstanding,
                electric,
                water,
                maintenance,
                operating,
                net,
                invoiceCount,
                visibleRoomCount,
                tenantCount,
                occupiedRooms,
                vacantRooms,
                maintenanceRooms,
                percent(BigDecimal.valueOf(occupiedRooms), roomTotal),
                percent(BigDecimal.valueOf(vacantRooms), roomTotal),
                percent(BigDecimal.valueOf(maintenanceRooms), roomTotal),
                percent(collected, revenue),
                percent(operating, revenue),
                trend,
                trend.compareTo(ZERO) >= 0
        );
    }

    private List<String> buildInsights(Overview overview,
                                       List<MonthRow> months,
                                       List<RoomRow> rooms,
                                       List<PropertyRow> properties) {
        List<String> insights = new ArrayList<>();
        if (overview.getRevenue().compareTo(ZERO) <= 0) {
            insights.add("Chưa có hóa đơn trong kỳ đã chọn. Hãy xuất hóa đơn từ trang Tính tiền phòng để báo cáo có dữ liệu.");
            return insights;
        }
        if (overview.getOutstanding().compareTo(ZERO) > 0) {
            insights.add("Còn " + MoneyFormatter.vnd(overview.getOutstanding()) + " chưa thu trong kỳ này, nên ưu tiên nhắc thanh toán các phòng còn công nợ.");
        }
        if (overview.getVacantRooms() > 0) {
            insights.add("Hiện có " + overview.getVacantRooms() + " phòng còn trống. Nên ưu tiên cập nhật ảnh, giá và tiện ích để tăng tỷ lệ lấp đầy.");
        }
        if (overview.getMaintenanceRooms() > 0) {
            insights.add(overview.getMaintenanceRooms() + " phòng đang bảo trì. Nên xử lý sớm để tránh giảm số phòng có thể cho thuê.");
        }
        rooms.stream().filter(r -> r.getCollected().compareTo(ZERO) > 0).findFirst()
                .ifPresent(r -> insights.add("Phòng thu tiền tốt nhất là " + r.getRoomCode() + " với " + MoneyFormatter.vnd(r.getCollected()) + " đã thu."));
        properties.stream().filter(p -> p.getCollected().compareTo(ZERO) > 0).findFirst()
                .ifPresent(p -> insights.add("Cơ sở nổi bật trong kỳ là " + p.getPropertyName() + ", đã thu " + MoneyFormatter.vnd(p.getCollected()) + "."));
        if (months.size() >= 2) {
            MonthRow last = months.get(months.size() - 1);
            MonthRow prev = months.get(months.size() - 2);
            BigDecimal change = percentChange(prev.getCollected(), last.getCollected());
            if (change.compareTo(new BigDecimal("10")) >= 0) {
                insights.add("Tiền đã thu tháng cuối kỳ tăng " + change.setScale(0, RoundingMode.HALF_UP) + "% so với tháng trước.");
            } else if (change.compareTo(new BigDecimal("-10")) <= 0) {
                insights.add("Tiền đã thu tháng cuối kỳ giảm " + change.abs().setScale(0, RoundingMode.HALF_UP) + "% so với tháng trước, cần kiểm tra hóa đơn chưa thanh toán.");
            }
        }
        return insights;
    }

    private YearMonth validPeriod(Integer year, Integer month, YearMonth fallback) {
        if (year == null || month == null || month < 1 || month > 12 || year < 2020 || year > 2100) {
            return fallback;
        }
        return YearMonth.of(year, month);
    }

    private boolean matchesProperty(Room room, UUID selectedPropertyId) {
        return selectedPropertyId == null || (room != null && Objects.equals(room.getPropertyId(), selectedPropertyId));
    }

    private BigDecimal collectedAmount(Invoice invoice) {
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) return money(invoice.getTotalAmount());
        return money(invoice.getPaidAmount()).min(money(invoice.getTotalAmount()));
    }

    private BigDecimal maintenanceCost(MaintenanceTicket ticket) {
        BigDecimal actual = money(ticket.getActualCost());
        if (actual.compareTo(ZERO) > 0) return actual;
        return money(ticket.getEstimatedCost());
    }

    private BigDecimal average(List<BigDecimal> values, int endInclusive, int window) {
        int start = Math.max(0, endInclusive - window + 1);
        BigDecimal total = ZERO;
        int count = 0;
        for (int i = start; i <= endInclusive; i++) {
            total = total.add(money(values.get(i)));
            count++;
        }
        return count == 0 ? ZERO : total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumMonths(List<MonthRow> rows, java.util.function.Function<MonthRow, BigDecimal> getter) {
        BigDecimal total = ZERO;
        for (MonthRow row : rows) total = total.add(money(getter.apply(row)));
        return total;
    }

    private static BigDecimal money(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private static BigDecimal max(BigDecimal current, BigDecimal... values) {
        BigDecimal result = money(current);
        for (BigDecimal value : values) {
            if (money(value).compareTo(result) > 0) result = money(value);
        }
        return result;
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (money(total).compareTo(ZERO) <= 0) return ZERO;
        return money(part).multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (money(previous).compareTo(ZERO) <= 0) {
            return money(current).compareTo(ZERO) > 0 ? BigDecimal.valueOf(100) : ZERO;
        }
        return money(current).subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private static int barHeight(BigDecimal value, BigDecimal max) {
        if (money(max).compareTo(ZERO) <= 0) return 8;
        int raw = money(value).multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(6, Math.min(100, raw));
    }

    private static String points(List<MonthRow> rows, BigDecimal max, String type) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MonthRow row : rows) {
            BigDecimal value = switch (type) {
                case "avg3" -> row.getAvg3();
                case "avg6" -> row.getAvg6();
                case "operating" -> row.getOperatingCost();
                default -> row.getRevenue();
            };
            double x = row.getChartX();
            double y = y(value, max);
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(String.format(Locale.US, "%.1f,%.1f", x, y));
        }
        return sb.toString();
    }

    private static String areaPath(List<MonthRow> rows,
                                   BigDecimal max,
                                   java.util.function.Function<MonthRow, BigDecimal> valueGetter) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        MonthRow first = rows.get(0);
        MonthRow last = rows.get(rows.size() - 1);
        sb.append(String.format(Locale.US, "M %.1f 220 ", first.getChartX()));
        for (MonthRow row : rows) {
            sb.append(String.format(Locale.US, "L %.1f %.1f ", row.getChartX(), y(valueGetter.apply(row), max)));
        }
        sb.append(String.format(Locale.US, "L %.1f 220 Z", last.getChartX()));
        return sb.toString();
    }

    private static double y(BigDecimal value, BigDecimal max) {
        double ratio = money(max).compareTo(ZERO) <= 0 ? 0 : money(value).doubleValue() / max.doubleValue();
        ratio = Math.max(0, Math.min(1, ratio));
        return 220.0 - ratio * 170.0;
    }

    private static double x(int index, int count) {
        if (count <= 1) return 500.0;
        return 54.0 + (892.0 * index / (count - 1));
    }

    private static String monthLabel(YearMonth period) {
        return String.format(Locale.ROOT, "%02d/%d", period.getMonthValue(), period.getYear());
    }

    private static class MonthAccumulator {
        private final YearMonth period;
        private BigDecimal revenue = ZERO;
        private BigDecimal collected = ZERO;
        private BigDecimal outstanding = ZERO;
        private BigDecimal electricCost = ZERO;
        private BigDecimal waterCost = ZERO;
        private BigDecimal maintenanceCost = ZERO;
        private int invoiceCount = 0;

        private MonthAccumulator(YearMonth period) {
            this.period = period;
        }

        private void addInvoice(BigDecimal billed, BigDecimal paid, BigDecimal debt) {
            revenue = revenue.add(money(billed));
            collected = collected.add(money(paid));
            outstanding = outstanding.add(money(debt));
            invoiceCount++;
        }

        private void addUtility(BigDecimal electric, BigDecimal water) {
            electricCost = electricCost.add(money(electric));
            waterCost = waterCost.add(money(water));
        }

        private void addMaintenance(BigDecimal cost) {
            maintenanceCost = maintenanceCost.add(money(cost));
        }

        private BigDecimal operatingCost() {
            return electricCost.add(waterCost).add(maintenanceCost);
        }

        private MonthRow toRow(BigDecimal avg3, BigDecimal avg6, int index, int count) {
            return new MonthRow(
                    period,
                    monthLabel(period),
                    revenue,
                    collected,
                    outstanding,
                    electricCost,
                    waterCost,
                    maintenanceCost,
                    operatingCost(),
                    avg3,
                    avg6,
                    invoiceCount,
                    8,
                    8,
                    8,
                    8,
                    8,
                    x(index, count),
                    220.0
            );
        }
    }

    private static class RoomAccumulator {
        private final Room room;
        private final Property property;
        private BigDecimal revenue = ZERO;
        private BigDecimal collected = ZERO;
        private BigDecimal outstanding = ZERO;
        private BigDecimal electricCost = ZERO;
        private BigDecimal waterCost = ZERO;
        private BigDecimal maintenanceCost = ZERO;
        private int invoiceCount = 0;

        private RoomAccumulator(Room room, Property property) {
            this.room = room;
            this.property = property;
        }

        private void addInvoice(BigDecimal billed, BigDecimal paid, BigDecimal debt) {
            revenue = revenue.add(money(billed));
            collected = collected.add(money(paid));
            outstanding = outstanding.add(money(debt));
            invoiceCount++;
        }

        private void addUtility(BigDecimal electric, BigDecimal water) {
            electricCost = electricCost.add(money(electric));
            waterCost = waterCost.add(money(water));
        }

        private void addMaintenance(BigDecimal cost) {
            maintenanceCost = maintenanceCost.add(money(cost));
        }

        private RoomRow toRow() {
            String code = room != null ? room.getCode() : "Không rõ";
            String title = room != null ? room.getTitle() : "Phòng không còn tồn tại";
            String propertyName = property != null ? property.getName() : "Chưa rõ cơ sở";
            BigDecimal operating = electricCost.add(waterCost).add(maintenanceCost);
            return new RoomRow(code, title, propertyName, revenue, collected, outstanding,
                    electricCost, waterCost, maintenanceCost, operating, collected.subtract(operating), invoiceCount);
        }
    }

    private static class PropertyAccumulator {
        private final Property property;
        private BigDecimal revenue = ZERO;
        private BigDecimal collected = ZERO;
        private BigDecimal outstanding = ZERO;
        private BigDecimal electricCost = ZERO;
        private BigDecimal waterCost = ZERO;
        private BigDecimal maintenanceCost = ZERO;
        private int invoiceCount = 0;
        private int roomCount = 0;
        private int occupiedRooms = 0;

        private PropertyAccumulator(Property property) {
            this.property = property;
        }

        private void addInvoice(BigDecimal billed, BigDecimal paid, BigDecimal debt) {
            revenue = revenue.add(money(billed));
            collected = collected.add(money(paid));
            outstanding = outstanding.add(money(debt));
            invoiceCount++;
        }

        private void addUtility(BigDecimal electric, BigDecimal water) {
            electricCost = electricCost.add(money(electric));
            waterCost = waterCost.add(money(water));
        }

        private void addMaintenance(BigDecimal cost) {
            maintenanceCost = maintenanceCost.add(money(cost));
        }

        private PropertyRow toRow() {
            BigDecimal operating = electricCost.add(waterCost).add(maintenanceCost);
            return new PropertyRow(property.getId(), property.getName(), roomCount, occupiedRooms,
                    revenue, collected, outstanding, electricCost, waterCost, maintenanceCost,
                    operating, collected.subtract(operating), invoiceCount);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ReportData {
        private final List<Property> properties;
        private final UUID selectedPropertyId;
        private final YearMonth fromPeriod;
        private final YearMonth toPeriod;
        private final Overview overview;
        private final List<MonthRow> months;
        private final List<RoomRow> rooms;
        private final List<PropertyRow> propertyRows;
        private final List<String> insights;
        private final String revenuePoints;
        private final String avg3Points;
        private final String avg6Points;
        private final String operatingPoints;
        private final String revenueAreaPath;
        private final BigDecimal chartMax;
    }

    @Getter
    @AllArgsConstructor
    public static class Overview {
        private final BigDecimal revenue;
        private final BigDecimal collected;
        private final BigDecimal outstanding;
        private final BigDecimal electricCost;
        private final BigDecimal waterCost;
        private final BigDecimal maintenanceCost;
        private final BigDecimal operatingCost;
        private final BigDecimal net;
        private final int invoiceCount;
        private final int roomCount;
        private final int tenantCount;
        private final int occupiedRooms;
        private final int vacantRooms;
        private final int maintenanceRooms;
        private final BigDecimal occupancyRate;
        private final BigDecimal vacantRate;
        private final BigDecimal maintenanceRate;
        private final BigDecimal collectionRate;
        private final BigDecimal costRatio;
        private final BigDecimal trendPercent;
        private final boolean trendUp;
    }

    @Getter
    @AllArgsConstructor
    public static class MonthRow {
        private final YearMonth period;
        private final String label;
        private final BigDecimal revenue;
        private final BigDecimal collected;
        private final BigDecimal outstanding;
        private final BigDecimal electricCost;
        private final BigDecimal waterCost;
        private final BigDecimal maintenanceCost;
        private final BigDecimal operatingCost;
        private final BigDecimal avg3;
        private final BigDecimal avg6;
        private final int invoiceCount;
        private final int revenueHeight;
        private final int operatingHeight;
        private final int collectedHeight;
        private final int outstandingHeight;
        private final int netHeight;
        private final double chartX;
        private final double chartY;

        private MonthRow withScale(BigDecimal max) {
            return new MonthRow(period, label, revenue, collected, outstanding, electricCost, waterCost,
                    maintenanceCost, operatingCost, avg3, avg6, invoiceCount,
                    barHeight(revenue, max),
                    barHeight(operatingCost, max),
                    barHeight(collected, max),
                    barHeight(outstanding, max),
                    barHeight(collected.subtract(operatingCost).abs(), max),
                    chartX,
                    y(revenue, max));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class RoomRow {
        private final String roomCode;
        private final String roomTitle;
        private final String propertyName;
        private final BigDecimal revenue;
        private final BigDecimal collected;
        private final BigDecimal outstanding;
        private final BigDecimal electricCost;
        private final BigDecimal waterCost;
        private final BigDecimal maintenanceCost;
        private final BigDecimal operatingCost;
        private final BigDecimal net;
        private final int invoiceCount;
    }

    @Getter
    @AllArgsConstructor
    public static class PropertyRow {
        private final UUID propertyId;
        private final String propertyName;
        private final int roomCount;
        private final int occupiedRooms;
        private final BigDecimal revenue;
        private final BigDecimal collected;
        private final BigDecimal outstanding;
        private final BigDecimal electricCost;
        private final BigDecimal waterCost;
        private final BigDecimal maintenanceCost;
        private final BigDecimal operatingCost;
        private final BigDecimal net;
        private final int invoiceCount;
    }
}
