package vn.glassliving.utility.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomBillingService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final UtilityReadingRepository utilityRepository;
    private final InvoiceService invoiceService;

    @Transactional(readOnly = true)
    public BillingView buildView(UUID ownerId, short year, short month,
                                 UUID propertyId, String q, String status) {
        List<Property> properties = propertyRepository.findByOwnerIdOrderByNameAsc(ownerId);
        Map<UUID, Property> propertyById = new HashMap<>();
        for (Property p : properties) {
            propertyById.put(p.getId(), p);
        }

        List<Room> rooms = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 2000)).getContent()
                .stream()
                .filter(r -> propertyId == null || propertyId.equals(r.getPropertyId()))
                .sorted(Comparator.comparing(r -> Objects.toString(r.getCode(), ""), String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<UUID, UtilityReading> readingByRoom = new HashMap<>();
        for (UtilityReading r : utilityRepository.findByOwnerIdAndPeriodYearAndPeriodMonth(ownerId, year, month)) {
            readingByRoom.put(r.getRoomId(), r);
        }

        Map<UUID, Invoice> invoiceByRoom = new HashMap<>();
        for (Room room : rooms) {
            invoiceRepository.findFirstByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
                    ownerId, room.getId(), year, month).ifPresent(inv -> invoiceByRoom.put(room.getId(), inv));
        }

        Map<UUID, User> usersById = new HashMap<>();
        List<UUID> tenantIds = rooms.stream()
                .map(Room::getCurrentTenantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!tenantIds.isEmpty()) {
            userRepository.findAllById(tenantIds).forEach(u -> usersById.put(u.getId(), u));
        }

        List<BillingRow> allRows = new ArrayList<>();
        for (Room room : rooms) {
            Property property = propertyById.get(room.getPropertyId());
            if (property == null) {
                continue;
            }
            User tenant = room.getCurrentTenantId() != null ? usersById.get(room.getCurrentTenantId()) : null;
            Contract contract = tenant != null
                    ? contractRepository.findFirstByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByStartDateDesc(
                            ownerId, room.getId(), tenant.getId(), Contract.ContractStatus.ACTIVE).orElse(null)
                    : null;
            BillingRow row = buildRow(ownerId, room, property, tenant, contract,
                    readingByRoom.get(room.getId()), invoiceByRoom.get(room.getId()), year, month);
            allRows.add(row);
        }

        List<BillingRow> filtered = allRows.stream()
                .filter(r -> matchesQuery(r, q))
                .filter(r -> matchesStatus(r, status))
                .toList();

        return new BillingView(filtered, summarize(filtered, allRows), properties, year, month, propertyId, q, status);
    }

    @Transactional(readOnly = true)
    public BillingDetail buildDetail(UUID ownerId, UUID roomId, short year, short month) {
        validatePeriod(year, month);
        Room room = loadOwnedRoom(ownerId, roomId);
        Property property = propertyRepository.findById(room.getPropertyId())
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        User tenant = room.getCurrentTenantId() != null
                ? userRepository.findById(room.getCurrentTenantId()).orElse(null)
                : null;
        Contract contract = tenant != null
                ? contractRepository.findFirstByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByStartDateDesc(
                ownerId, room.getId(), tenant.getId(), Contract.ContractStatus.ACTIVE).orElse(null)
                : null;
        UtilityReading reading = utilityRepository.findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(
                ownerId, roomId, year, month).orElse(null);
        Invoice invoice = invoiceRepository.findFirstByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
                ownerId, roomId, year, month).orElse(null);

        BillingRow row = buildRow(ownerId, room, property, tenant, contract, reading, invoice, year, month);
        List<Invoice> invoices = invoiceRepository
                .findTop18ByOwnerIdAndRoomIdOrderByPeriodYearDescPeriodMonthDescCreatedAtDesc(ownerId, roomId);
        Map<UUID, User> usersById = new HashMap<>();
        List<UUID> tenantIds = invoices.stream()
                .map(Invoice::getTenantUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!tenantIds.isEmpty()) {
            userRepository.findAllById(tenantIds).forEach(u -> usersById.put(u.getId(), u));
        }
        List<InvoiceHistoryRow> history = invoices.stream()
                .map(inv -> new InvoiceHistoryRow(inv, usersById.get(inv.getTenantUserId()), invoiceService.parseOtherItems(inv)))
                .toList();

        return new BillingDetail(row, history, invoiceOtherItems(room, property), year, month);
    }

    @Transactional
    public UtilityReading saveReading(UUID ownerId, UUID roomId, short year, short month,
                                      LocalDate readingDate,
                                      BigDecimal electricPrev, BigDecimal electricCurr,
                                      BigDecimal waterPrev, BigDecimal waterCurr,
                                      String note) {
        validatePeriod(year, month);
        Room room = loadOwnedRoom(ownerId, roomId);
        Property property = propertyRepository.findById(room.getPropertyId())
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        Contract contract = resolveActiveContract(ownerId, room);
        Invoice existingInvoice = findInvoiceForPeriod(ownerId, room, contract, year, month);
        if (existingInvoice != null && existingInvoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("Hóa đơn kỳ này đã hủy. Không thể sửa chỉ số điện nước.");
        }
        Tariff tariff = resolveTariff(room, property, contract);

        UtilityReading previous = previousReading(ownerId, roomId, year, month);
        BigDecimal defaultElectricPrev = previous != null ? nz(previous.getElectricCurr()) : BigDecimal.ZERO;
        BigDecimal defaultWaterPrev = previous != null ? nz(previous.getWaterCurr()) : BigDecimal.ZERO;

        BigDecimal ePrev = defaultElectricPrev;
        BigDecimal wPrev = defaultWaterPrev;
        BigDecimal eCurr = electricCurr != null ? electricCurr : ePrev;
        BigDecimal wCurr = waterCurr != null ? waterCurr : wPrev;

        if (eCurr.compareTo(ePrev) < 0) {
            throw BusinessException.badRequest("Chỉ số điện mới không được nhỏ hơn chỉ số điện cũ.");
        }
        if (wCurr.compareTo(wPrev) < 0) {
            throw BusinessException.badRequest("Chỉ số nước mới không được nhỏ hơn chỉ số nước cũ.");
        }

        UtilityReading reading = utilityRepository.findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(
                        ownerId, roomId, year, month)
                .orElseGet(() -> UtilityReading.builder()
                        .ownerId(ownerId)
                        .propertyId(room.getPropertyId())
                        .roomId(roomId)
                        .periodYear(year)
                        .periodMonth(month)
                        .build());

        reading.setPropertyId(room.getPropertyId());
        reading.setReadingDate(readingDate != null ? readingDate : LocalDate.now());
        reading.setElectricPrev(ePrev);
        reading.setElectricCurr(eCurr);
        reading.setWaterPrev(wPrev);
        reading.setWaterCurr(wCurr);
        reading.setElectricUnitPrice(tariff.electricUnit());
        reading.setWaterUnitPrice(tariff.waterUnit());
        reading.setNote(note != null && !note.isBlank() ? note.trim() : null);
        reading.setLocked(false);
        reading.recompute();
        UtilityReading saved = utilityRepository.save(reading);
        if (existingInvoice != null) {
            refreshInvoice(ownerId, existingInvoice, saved, room, property);
        }
        return saved;
    }

    @Transactional
    public Invoice saveReadingAndIssue(UUID ownerId, UUID roomId, short year, short month,
                                       LocalDate readingDate,
                                       BigDecimal electricPrev, BigDecimal electricCurr,
                                       BigDecimal waterPrev, BigDecimal waterCurr,
                                       String note) {
        saveReading(ownerId, roomId, year, month, readingDate,
                electricPrev, electricCurr, waterPrev, waterCurr, note);
        Room room = loadOwnedRoom(ownerId, roomId);
        Property property = propertyRepository.findById(room.getPropertyId())
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        Contract contract = resolveOrCreateBillingContract(ownerId, room, property, year, month);
        UtilityReading reading = utilityRepository.findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(
                        ownerId, roomId, year, month)
                .orElseThrow(() -> BusinessException.conflict("Chưa có chỉ số điện nước cho kỳ này."));
        Invoice existing = findInvoiceForPeriod(ownerId, room, contract, year, month);
        if (existing != null) {
            return refreshInvoice(ownerId, existing, reading, room, property);
        }
        return invoiceService.createDetailed(ownerId, contract.getId(), year, month,
                reading.getElectricPrev(), reading.getElectricCurr(),
                reading.getWaterPrev(), reading.getWaterCurr(),
                invoiceOtherItems(room, property), BigDecimal.ZERO);
    }

    @Transactional
    public int lockPeriod(UUID ownerId, short year, short month, UUID propertyId) {
        BillingView view = buildView(ownerId, year, month, propertyId, null, null);
        List<BillingRow> missing = view.getRows().stream()
                .filter(BillingRow::isOccupied)
                .filter(row -> !row.isHasReading())
                .toList();
        if (!missing.isEmpty()) {
            throw BusinessException.conflict("Còn " + missing.size() + " phòng đang thuê chưa nhập chỉ số. Hãy nhập đủ trước khi chốt kỳ.");
        }

        int locked = 0;
        for (BillingRow row : view.getRows()) {
            if (row.getReading() != null && !row.getReading().isLocked()) {
                row.getReading().setLocked(true);
                utilityRepository.save(row.getReading());
                locked++;
            }
        }
        return locked;
    }

    @Transactional
    public Invoice createInvoice(UUID ownerId, UUID roomId, short year, short month) {
        validatePeriod(year, month);
        Room room = loadOwnedRoom(ownerId, roomId);
        if (room.getCurrentTenantId() == null) {
            throw BusinessException.conflict("Phòng đang trống, không thể tạo hóa đơn.");
        }
        Property property = propertyRepository.findById(room.getPropertyId())
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        Contract contract = resolveOrCreateBillingContract(ownerId, room, property, year, month);
        UtilityReading reading = utilityRepository.findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(ownerId, roomId, year, month)
                .orElseThrow(() -> BusinessException.conflict("Chưa có chỉ số điện nước cho kỳ này."));
        Invoice existing = findInvoiceForPeriod(ownerId, room, contract, year, month);
        if (existing != null) {
            return refreshInvoice(ownerId, existing, reading, room, property);
        }
        return invoiceService.createDetailed(ownerId, contract.getId(), year, month,
                reading.getElectricPrev(), reading.getElectricCurr(),
                reading.getWaterPrev(), reading.getWaterCurr(),
                invoiceOtherItems(room, property), BigDecimal.ZERO);
    }

    private BillingRow buildRow(UUID ownerId, Room room, Property property, User tenant, Contract contract,
                                UtilityReading reading, Invoice invoice, short year, short month) {
        UtilityReading previous = previousReading(ownerId, room.getId(), year, month);
        BigDecimal electricPrev = reading != null ? nz(reading.getElectricPrev()) : (previous != null ? nz(previous.getElectricCurr()) : BigDecimal.ZERO);
        BigDecimal waterPrev = reading != null ? nz(reading.getWaterPrev()) : (previous != null ? nz(previous.getWaterCurr()) : BigDecimal.ZERO);
        BigDecimal electricCurr = reading != null ? nz(reading.getElectricCurr()) : electricPrev;
        BigDecimal waterCurr = reading != null ? nz(reading.getWaterCurr()) : waterPrev;

        Tariff tariff = resolveTariff(room, property, contract);
        BigDecimal electricUsage = electricCurr.subtract(electricPrev);
        BigDecimal waterUsage = waterCurr.subtract(waterPrev);
        boolean invalidReading = electricUsage.signum() < 0 || waterUsage.signum() < 0;
        if (electricUsage.signum() < 0) electricUsage = BigDecimal.ZERO;
        if (waterUsage.signum() < 0) waterUsage = BigDecimal.ZERO;

        BigDecimal electricAmount = reading != null
                ? nz(reading.getElectricAmount())
                : electricUsage.multiply(tariff.electricUnit()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal waterAmount = reading != null
                ? nz(reading.getWaterAmount())
                : waterUsage.multiply(tariff.waterUnit()).setScale(0, RoundingMode.HALF_UP);

        BigDecimal rentAmount = contract != null ? nz(contract.getRentMonthly()) : nz(room.getPriceMonthly());
        BigDecimal serviceFee = contract != null ? nz(contract.getServiceFee()) : nz(room.getServiceFee());
        List<FeeLine> feeLines = new ArrayList<>();
        if (serviceFee.signum() > 0) feeLines.add(new FeeLine("Dịch vụ", serviceFee));
        addFee(feeLines, "Internet", property.getInternetFee());
        addFee(feeLines, "Rác", property.getGarbageFee());
        addFee(feeLines, "Quản lý", property.getManagementFee());
        if (property.getExtraFees() != null) {
            for (Property.ExtraFee fee : property.getExtraFees()) {
                addFee(feeLines, fee.getName(), fee.getAmount());
            }
        }
        if (room.getExtraFees() != null) {
            for (Property.ExtraFee fee : room.getExtraFees()) {
                addFee(feeLines, fee.getName(), fee.getAmount());
            }
        }

        BigDecimal fixedFeeTotal = feeLines.stream()
                .map(FeeLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean occupied = tenant != null;
        BigDecimal totalAmount = occupied
                ? rentAmount.add(fixedFeeTotal).add(electricAmount).add(waterAmount)
                : BigDecimal.ZERO;

        UsageAverages averages = usageAverages(room.getId(), year, month);
        boolean electricAnomaly = isAnomaly(electricUsage, averages.electricAvg(), new BigDecimal("20"));
        boolean waterAnomaly = isAnomaly(waterUsage, averages.waterAvg(), new BigDecimal("5"));

        String status = resolveStatus(occupied, reading, invoice, contract, invalidReading, electricAnomaly || waterAnomaly);
        return BillingRow.builder()
                .room(room)
                .property(property)
                .tenant(tenant)
                .contract(contract)
                .reading(reading)
                .invoice(invoice)
                .year(year)
                .month(month)
                .occupied(occupied)
                .hasReading(reading != null)
                .hasInvoice(invoice != null)
                .locked(reading != null && reading.isLocked())
                .invalidReading(invalidReading)
                .electricAnomaly(electricAnomaly)
                .waterAnomaly(waterAnomaly)
                .rentAmount(rentAmount)
                .fixedFeeTotal(fixedFeeTotal)
                .serviceFee(serviceFee)
                .electricPrev(electricPrev)
                .electricCurr(electricCurr)
                .electricUsage(electricUsage)
                .electricUnitPrice(tariff.electricUnit())
                .electricAmount(electricAmount)
                .waterPrev(waterPrev)
                .waterCurr(waterCurr)
                .waterUsage(waterUsage)
                .waterUnitPrice(tariff.waterUnit())
                .waterAmount(waterAmount)
                .totalAmount(totalAmount)
                .averageElectricUsage(averages.electricAvg())
                .averageWaterUsage(averages.waterAvg())
                .status(status)
                .statusLabel(statusLabel(status))
                .statusBadge(statusBadge(status))
                .feeLines(feeLines)
                .invoiceReady(occupied && reading != null && invoice == null && !invalidReading)
                .warning(warningFor(status, contract, electricAnomaly, waterAnomaly))
                .build();
    }

    private Summary summarize(List<BillingRow> rows, List<BillingRow> allRows) {
        BigDecimal total = rows.stream().map(BillingRow::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rent = rows.stream().filter(BillingRow::isOccupied).map(BillingRow::getRentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fixed = rows.stream().filter(BillingRow::isOccupied).map(BillingRow::getFixedFeeTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal electric = rows.stream().map(BillingRow::getElectricAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal water = rows.stream().map(BillingRow::getWaterAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long occupied = allRows.stream().filter(BillingRow::isOccupied).count();
        long missing = allRows.stream().filter(BillingRow::isOccupied).filter(r -> !r.isHasReading()).count();
        long anomalies = allRows.stream().filter(r -> r.isElectricAnomaly() || r.isWaterAnomaly() || r.isInvalidReading()).count();
        long ready = allRows.stream().filter(BillingRow::isInvoiceReady).count();
        long invoiced = allRows.stream().filter(BillingRow::isHasInvoice).count();
        long locked = allRows.stream().filter(BillingRow::isLocked).count();
        return new Summary(rows.size(), allRows.size(), occupied, missing, anomalies, ready, invoiced, locked,
                total, rent, fixed, electric, water);
    }

    private boolean matchesStatus(BillingRow row, String status) {
        if (status == null || status.isBlank() || "all".equals(status)) return true;
        return switch (status) {
            case "missing" -> row.isOccupied() && !row.isHasReading();
            case "ready" -> row.isInvoiceReady();
            case "anomaly" -> row.isInvalidReading() || row.isElectricAnomaly() || row.isWaterAnomaly();
            case "invoiced" -> row.isHasInvoice();
            case "paid" -> row.getInvoice() != null && row.getInvoice().getStatus() == Invoice.InvoiceStatus.PAID;
            case "empty" -> !row.isOccupied();
            case "locked" -> row.isLocked();
            default -> true;
        };
    }

    private boolean matchesQuery(BillingRow row, String q) {
        if (q == null || q.isBlank()) return true;
        String needle = normalize(q);
        String blob = normalize(String.join(" ",
                Objects.toString(row.getRoom().getCode(), ""),
                Objects.toString(row.getRoom().getTitle(), ""),
                Objects.toString(row.getProperty().getName(), ""),
                row.getTenant() != null ? row.getTenant().getFullName() : "",
                row.getTenant() != null ? row.getTenant().getEmail() : "",
                row.getTenant() != null ? Objects.toString(row.getTenant().getPhone(), "") : ""));
        return blob.contains(needle);
    }

    private UtilityReading previousReading(UUID ownerId, UUID roomId, short year, short month) {
        YearMonth previous = YearMonth.of(year, month).minusMonths(1);
        return utilityRepository.findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(
                ownerId,
                roomId,
                (short) previous.getYear(),
                (short) previous.getMonthValue()).orElse(null);
    }

    private UsageAverages usageAverages(UUID roomId, short year, short month) {
        List<UtilityReading> previous = utilityRepository.findPreviousReadings(roomId, year, month, PageRequest.of(0, 3));
        if (previous.isEmpty()) return new UsageAverages(BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal e = BigDecimal.ZERO;
        BigDecimal w = BigDecimal.ZERO;
        for (UtilityReading reading : previous) {
            e = e.add(reading.getElectricUsage());
            w = w.add(reading.getWaterUsage());
        }
        BigDecimal count = BigDecimal.valueOf(previous.size());
        return new UsageAverages(e.divide(count, 2, RoundingMode.HALF_UP), w.divide(count, 2, RoundingMode.HALF_UP));
    }

    private boolean isAnomaly(BigDecimal usage, BigDecimal avg, BigDecimal floor) {
        if (usage == null || usage.signum() <= 0 || avg == null || avg.signum() <= 0) return false;
        return usage.compareTo(floor) > 0 && usage.compareTo(avg.multiply(new BigDecimal("1.5"))) > 0;
    }

    private Contract resolveActiveContract(UUID ownerId, Room room) {
        if (room.getCurrentTenantId() == null) return null;
        return contractRepository.findFirstByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByStartDateDesc(
                ownerId, room.getId(), room.getCurrentTenantId(), Contract.ContractStatus.ACTIVE).orElse(null);
    }

    private Room loadOwnedRoom(UUID ownerId, UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!ownerId.equals(room.getOwnerId())) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        return room;
    }

    private Tariff resolveTariff(Room room, Property property, Contract contract) {
        if (contract != null && positive(contract.getElectricUnit()) && positive(contract.getWaterUnit())) {
            return new Tariff(contract.getElectricUnit(), contract.getWaterUnit());
        }
        if (room.isInheritTariff()) {
            return new Tariff(nz(property.getElectricUnit()), nz(property.getWaterUnit()));
        }
        return new Tariff(nz(room.getElectricUnit()), nz(room.getWaterUnit()));
    }

    private BigDecimal propertyFixedFees(Property property) {
        BigDecimal sum = nz(property.getInternetFee()).add(nz(property.getGarbageFee())).add(nz(property.getManagementFee()));
        if (property.getExtraFees() != null) {
            for (Property.ExtraFee fee : property.getExtraFees()) {
                sum = sum.add(nz(fee.getAmount()));
            }
        }
        return sum;
    }

    private List<InvoiceLineItem> invoiceOtherItems(Room room, Property property) {
        List<InvoiceLineItem> lines = new ArrayList<>();
        addInvoiceItem(lines, "Internet", property.getInternetFee());
        addInvoiceItem(lines, "Rác", property.getGarbageFee());
        addInvoiceItem(lines, "Quản lý", property.getManagementFee());
        if (property.getExtraFees() != null) {
            for (Property.ExtraFee fee : property.getExtraFees()) {
                addInvoiceItem(lines, fee.getName(), fee.getAmount());
            }
        }
        if (room.getExtraFees() != null) {
            for (Property.ExtraFee fee : room.getExtraFees()) {
                addInvoiceItem(lines, fee.getName(), fee.getAmount());
            }
        }
        return lines;
    }

    private static void addInvoiceItem(List<InvoiceLineItem> lines, String name, BigDecimal amount) {
        BigDecimal safe = nz(amount);
        if (safe.signum() > 0) {
            lines.add(new InvoiceLineItem((name == null || name.isBlank()) ? "Phí khác" : name.trim(), safe));
        }
    }

    private static void addFee(List<FeeLine> lines, String name, BigDecimal amount) {
        BigDecimal safe = nz(amount);
        if (safe.signum() > 0) {
            lines.add(new FeeLine((name == null || name.isBlank()) ? "Phí khác" : name.trim(), safe));
        }
    }

    private String resolveStatus(boolean occupied, UtilityReading reading, Invoice invoice, Contract contract,
                                 boolean invalidReading, boolean anomaly) {
        if (!occupied) return "empty";
        if (invoice != null && invoice.getStatus() == Invoice.InvoiceStatus.PAID) return "paid";
        if (invoice != null) return "invoiced";
        if (reading == null) return "missing";
        if (invalidReading) return "invalid";
        if (anomaly) return "anomaly";
        if (reading.isLocked()) return "locked";
        return "ready";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "empty" -> "Phòng trống";
            case "no_contract" -> "Cần tạo hóa đơn";
            case "paid" -> "Đã thanh toán";
            case "invoiced" -> "Chờ thanh toán";
            case "missing" -> "Thiếu chỉ số";
            case "invalid" -> "Sai chỉ số";
            case "anomaly" -> "Bất thường";
            case "locked" -> "Đã chốt";
            default -> "Sẵn sàng";
        };
    }

    private String statusBadge(String status) {
        return switch (status) {
            case "empty" -> "badge-mute";
            case "no_contract", "missing", "anomaly" -> "badge-amber";
            case "invalid" -> "badge-rose";
            case "invoiced" -> "badge-amber";
            case "paid", "locked", "ready" -> "badge-emerald";
            default -> "badge-violet";
        };
    }

    private String warningFor(String status, Contract contract, boolean electricAnomaly, boolean waterAnomaly) {
        if ("missing".equals(status)) return "Cần nhập chỉ số điện nước cho kỳ này.";
        if ("no_contract".equals(status)) return "Có khách đang ở nhưng chưa có hồ sơ thu tiền. Khi xuất hóa đơn, hệ thống sẽ tự tạo theo thông tin phòng hiện tại.";
        if ("invalid".equals(status)) return "Chỉ số mới nhỏ hơn chỉ số cũ.";
        if (electricAnomaly && waterAnomaly) return "Điện và nước tăng cao so với trung bình 3 tháng.";
        if (electricAnomaly) return "Điện tăng cao so với trung bình 3 tháng.";
        if (waterAnomaly) return "Nước tăng cao so với trung bình 3 tháng.";
        if (contract == null) return "Khi xuất hóa đơn, hệ thống sẽ tự tạo hồ sơ thu tiền theo thông tin phòng hiện tại.";
        return "";
    }

    private Contract resolveOrCreateBillingContract(UUID ownerId, Room room, Property property, short year, short month) {
        Contract active = resolveActiveContract(ownerId, room);
        if (active != null) return active;
        if (room.getCurrentTenantId() == null) return null;

        Tariff tariff = resolveTariff(room, property, null);
        LocalDate start = room.getCurrentTenantStartedOn() != null
                ? room.getCurrentTenantStartedOn()
                : YearMonth.of(year, month).atDay(1);
        LocalDate end = start.plusMonths(12);
        if (!end.isAfter(start)) {
            end = start.plusMonths(1);
        }

        Contract contract = Contract.builder()
                .code(nextAutoContractCode(year, month))
                .roomId(room.getId())
                .ownerId(ownerId)
                .tenantUserId(room.getCurrentTenantId())
                .startDate(start)
                .endDate(end)
                .durationMonths((short) 12)
                .rentMonthly(nz(room.getPriceMonthly()))
                .depositAmount(nz(room.getDepositAmount()))
                .serviceFee(nz(room.getServiceFee()))
                .electricUnit(tariff.electricUnit())
                .waterUnit(tariff.waterUnit())
                .billingDay(property.getBillingDayDefault() != null ? property.getBillingDayDefault() : (short) 1)
                .status(Contract.ContractStatus.ACTIVE)
                .extraTerms("Tự tạo từ màn hình tính tiền phòng để phát hành hóa đơn.")
                .build();
        return contractRepository.save(contract);
    }

    private String nextAutoContractCode(short year, short month) {
        for (int i = 0; i < 8; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
            String code = "AUTO-" + String.format("%04d%02d", (int) year, (int) month) + "-" + suffix;
            if (!contractRepository.existsByCode(code)) {
                return code;
            }
        }
        return "AUTO-" + String.format("%04d%02d", (int) year, (int) month) + "-" + System.currentTimeMillis() % 1000000;
    }

    private Invoice findInvoiceForPeriod(UUID ownerId, Room room, Contract contract, short year, short month) {
        Invoice byRoom = invoiceRepository.findFirstByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
                ownerId, room.getId(), year, month).orElse(null);
        if (byRoom != null) {
            return byRoom;
        }
        if (contract == null) {
            return null;
        }
        return invoiceRepository.findFirstByOwnerIdAndContractIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
                        ownerId, contract.getId(), year, month)
                .or(() -> invoiceRepository.findAnyByOwnerIdAndContractIdAndPeriod(
                        ownerId, contract.getId(), year, month))
                .orElse(null);
    }

    private Invoice refreshInvoice(UUID ownerId, Invoice invoice, UtilityReading reading, Room room, Property property) {
        return invoiceService.refreshDetailed(ownerId, invoice,
                reading.getElectricPrev(), reading.getElectricCurr(),
                reading.getWaterPrev(), reading.getWaterCurr(),
                invoiceOtherItems(room, property), null);
    }

    private static void validatePeriod(short year, short month) {
        if (year < 2020 || year > 2100) throw BusinessException.badRequest("Năm không hợp lệ.");
        if (month < 1 || month > 12) throw BusinessException.badRequest("Tháng không hợp lệ.");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String normalize(String value) {
        String text = Objects.toString(value, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isBlank()) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9@.\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record Tariff(BigDecimal electricUnit, BigDecimal waterUnit) {}
    private record UsageAverages(BigDecimal electricAvg, BigDecimal waterAvg) {}

    @Getter
    @AllArgsConstructor
    public static class BillingView {
        private List<BillingRow> rows;
        private Summary summary;
        private List<Property> properties;
        private short year;
        private short month;
        private UUID propertyId;
        private String q;
        private String status;
    }

    @Getter
    @AllArgsConstructor
    public static class BillingDetail {
        private BillingRow row;
        private List<InvoiceHistoryRow> history;
        private List<InvoiceLineItem> otherItems;
        private short year;
        private short month;
    }

    @Getter
    @AllArgsConstructor
    public static class InvoiceHistoryRow {
        private Invoice invoice;
        private User tenant;
        private List<InvoiceLineItem> otherItems;
    }

    @Getter
    @Builder
    public static class BillingRow {
        private Room room;
        private Property property;
        private User tenant;
        private Contract contract;
        private UtilityReading reading;
        private Invoice invoice;
        private short year;
        private short month;
        private boolean occupied;
        private boolean hasReading;
        private boolean hasInvoice;
        private boolean locked;
        private boolean invalidReading;
        private boolean electricAnomaly;
        private boolean waterAnomaly;
        private boolean invoiceReady;
        private BigDecimal rentAmount;
        private BigDecimal fixedFeeTotal;
        private BigDecimal serviceFee;
        private BigDecimal electricPrev;
        private BigDecimal electricCurr;
        private BigDecimal electricUsage;
        private BigDecimal electricUnitPrice;
        private BigDecimal electricAmount;
        private BigDecimal waterPrev;
        private BigDecimal waterCurr;
        private BigDecimal waterUsage;
        private BigDecimal waterUnitPrice;
        private BigDecimal waterAmount;
        private BigDecimal totalAmount;
        private BigDecimal averageElectricUsage;
        private BigDecimal averageWaterUsage;
        private String status;
        private String statusLabel;
        private String statusBadge;
        private String warning;
        private List<FeeLine> feeLines;
    }

    @Getter
    @AllArgsConstructor
    public static class FeeLine {
        private String name;
        private BigDecimal amount;
    }

    @Getter
    @AllArgsConstructor
    public static class Summary {
        private long visibleRooms;
        private long totalRooms;
        private long occupiedRooms;
        private long missingReadings;
        private long anomalies;
        private long readyToInvoice;
        private long invoicedRooms;
        private long lockedReadings;
        private BigDecimal totalAmount;
        private BigDecimal rentAmount;
        private BigDecimal fixedFeeAmount;
        private BigDecimal electricAmount;
        private BigDecimal waterAmount;
    }
}
