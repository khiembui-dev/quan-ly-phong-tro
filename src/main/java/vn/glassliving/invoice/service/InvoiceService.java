package vn.glassliving.invoice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.automation.service.AutomationEmailService;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final UUID NO_FILTER_UUID = new UUID(0L, 0L);
    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(9999, 12, 31);

    private static final List<Invoice.InvoiceStatus> AUTO_CANCEL_STATUSES = List.of(
            Invoice.InvoiceStatus.PENDING,
            Invoice.InvoiceStatus.OVERDUE
    );

    private final InvoiceRepository invoiceRepository;
    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AutomationEmailService automationEmailService;

    @Transactional(readOnly = true)
    public Page<Invoice> findAdminInvoices(UUID ownerId,
                                           String q,
                                           UUID propertyId,
                                           UUID roomId,
                                           Invoice.InvoiceStatus status,
                                           YearMonth month,
                                           LocalDate fromDate,
                                           LocalDate toDate,
                                           Pageable pageable) {
        boolean dateRangeActive = fromDate != null || toDate != null;
        Short monthFilter = !dateRangeActive && month != null ? (short) month.getMonthValue() : (short) 0;
        Short yearFilter = !dateRangeActive && month != null ? (short) month.getYear() : (short) 0;

        return invoiceRepository.searchByOwnerId(
                ownerId,
                status != null ? status.name() : "",
                cleanSearch(q),
                propertyId != null ? propertyId : NO_FILTER_UUID,
                monthFilter,
                yearFilter,
                fromDate != null ? fromDate : MIN_FILTER_DATE,
                toDate != null ? toDate : MAX_FILTER_DATE,
                roomId != null ? roomId : NO_FILTER_UUID,
                pageable
        );
    }

    /**
     * Create a single invoice for one contract for a given period.
     * Smart: pulls rent + service fee + electric/water unit prices from contract,
     * computes electric/water amount from current readings vs previous,
     * checks for duplicate (one invoice per contract per period).
     */
    @Transactional
    public Invoice create(UUID ownerId, UUID contractId,
                          short year, short month,
                          BigDecimal electricPrev, BigDecimal electricCurr,
                          BigDecimal waterPrev,    BigDecimal waterCurr,
                          BigDecimal otherAmount,
                          BigDecimal discountAmount) {
        return createInternal(ownerId, contractId, year, month,
                electricPrev, electricCurr, waterPrev, waterCurr,
                otherAmount, discountAmount, null, true);
    }

    /**
     * Create an invoice with named non-utility fee lines already resolved by
     * the billing screen. This keeps the customer invoice detail readable.
     */
    @Transactional
    public Invoice createDetailed(UUID ownerId, UUID contractId,
                                  short year, short month,
                                  BigDecimal electricPrev, BigDecimal electricCurr,
                                  BigDecimal waterPrev, BigDecimal waterCurr,
                                  List<InvoiceLineItem> otherItems,
                                  BigDecimal discountAmount) {
        BigDecimal otherAmount = sumLineItems(otherItems);
        return createInternal(ownerId, contractId, year, month,
                electricPrev, electricCurr, waterPrev, waterCurr,
                otherAmount, discountAmount, otherItems, false);
    }

    @Transactional
    public Invoice refreshDetailed(UUID ownerId, Invoice inv,
                                   BigDecimal electricPrev, BigDecimal electricCurr,
                                   BigDecimal waterPrev, BigDecimal waterCurr,
                                   List<InvoiceLineItem> otherItems,
                                   BigDecimal discountAmount) {
        if (inv == null) {
            throw BusinessException.notFound("Hóa đơn");
        }
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        if (inv.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("Hóa đơn kỳ này đã hủy, không thể cập nhật lại.");
        }

        Contract c = contractRepository.findById(inv.getContractId())
                .orElseThrow(() -> BusinessException.notFound("Hồ sơ thu tiền"));
        if (!c.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hồ sơ thu tiền này.");
        }

        BigDecimal[] tariff = resolveTariff(c);
        BigDecimal electricAmount = computeAmount(electricPrev, electricCurr, tariff[0]);
        BigDecimal waterAmount = computeAmount(waterPrev, waterCurr, tariff[1]);
        BigDecimal other = sumLineItems(otherItems);
        BigDecimal discount = discountAmount != null ? nz(discountAmount) : nz(inv.getDiscountAmount());

        BigDecimal total = nz(c.getRentMonthly())
                .add(nz(c.getServiceFee()))
                .add(electricAmount)
                .add(waterAmount)
                .add(other)
                .add(nz(inv.getLateFeeAmount()))
                .subtract(discount);
        if (total.signum() < 0) total = BigDecimal.ZERO;

        if (inv.isDeleted()) {
            inv.setDeleted(false);
        }
        inv.setTenantUserId(c.getTenantUserId());
        inv.setRoomId(c.getRoomId());
        inv.setRentAmount(nz(c.getRentMonthly()));
        inv.setServiceAmount(nz(c.getServiceFee()));
        inv.setElectricPrev(electricPrev);
        inv.setElectricCurr(electricCurr);
        inv.setElectricAmount(electricAmount);
        inv.setWaterPrev(waterPrev);
        inv.setWaterCurr(waterCurr);
        inv.setWaterAmount(waterAmount);
        inv.setOtherItemsJson(writeOtherItems(otherItems));
        inv.setOtherAmount(other);
        inv.setDiscountAmount(discount);
        inv.setTotalAmount(total);

        if (inv.getStatus() == Invoice.InvoiceStatus.PAID) {
            inv.setPaidAmount(total);
        } else if (nz(inv.getPaidAmount()).compareTo(total) > 0) {
            inv.setPaidAmount(total);
        }
        return invoiceRepository.save(inv);
    }

    private Invoice createInternal(UUID ownerId, UUID contractId,
                                   short year, short month,
                                   BigDecimal electricPrev, BigDecimal electricCurr,
                                   BigDecimal waterPrev, BigDecimal waterCurr,
                                   BigDecimal otherAmount,
                                   BigDecimal discountAmount,
                                   List<InvoiceLineItem> otherItems,
                                   boolean includeRoomExtras) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> BusinessException.notFound("Hồ sơ thu tiền"));
        if (!c.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hồ sơ thu tiền này.");
        }
        if (invoiceRepository.existsByContractIdAndPeriodYearAndPeriodMonth(contractId, year, month)) {
            throw BusinessException.conflict("Hóa đơn cho kỳ " + month + "/" + year + " đã tồn tại.");
        }

        // Resolve effective tariffs: contract snapshot wins; if missing, fall back to room (respecting inherit_tariff) → property
        BigDecimal[] tariff = resolveTariff(c);
        BigDecimal electricUnit = tariff[0];
        BigDecimal waterUnit    = tariff[1];

        BigDecimal electricAmount = computeAmount(electricPrev, electricCurr, electricUnit);
        BigDecimal waterAmount    = computeAmount(waterPrev,    waterCurr,    waterUnit);
        BigDecimal other          = nz(otherAmount);
        BigDecimal discount       = nz(discountAmount);

        // Legacy invoice creation still auto-adds room extras. The room billing
        // flow passes fully named fee lines, so it disables this auto-add.
        BigDecimal roomExtras = includeRoomExtras ? sumRoomExtras(c.getRoomId()) : BigDecimal.ZERO;
        List<InvoiceLineItem> storedOtherItems = normalizeLineItems(otherItems);
        if (includeRoomExtras && roomExtras.signum() > 0) {
            storedOtherItems.add(new InvoiceLineItem("Phí phòng bổ sung", roomExtras));
        }
        if ((otherItems == null || otherItems.isEmpty()) && other.signum() > 0) {
            storedOtherItems.add(new InvoiceLineItem("Phí khác", other));
        }
        BigDecimal storedOtherAmount = other.add(roomExtras);

        BigDecimal total = c.getRentMonthly()
                .add(c.getServiceFee())
                .add(electricAmount)
                .add(waterAmount)
                .add(storedOtherAmount)
                .subtract(discount);
        if (total.signum() < 0) total = BigDecimal.ZERO;

        // Issue date = today, due date = today + 10 days
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(10);

        String code = nextSequentialInvoiceCode();

        Invoice inv = Invoice.builder()
                .code(code)
                .contractId(contractId)
                .ownerId(ownerId)
                .tenantUserId(c.getTenantUserId())
                .roomId(c.getRoomId())
                .periodYear(year)
                .periodMonth(month)
                .issueDate(issueDate)
                .dueDate(dueDate)
                .rentAmount(c.getRentMonthly())
                .serviceAmount(c.getServiceFee())
                .electricPrev(electricPrev)
                .electricCurr(electricCurr)
                .electricAmount(electricAmount)
                .waterPrev(waterPrev)
                .waterCurr(waterCurr)
                .waterAmount(waterAmount)
                .otherItemsJson(writeOtherItems(storedOtherItems))
                .otherAmount(storedOtherAmount)
                .discountAmount(discount)
                .totalAmount(total)
                .paidAmount(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.PENDING)
                .build();
        inv = invoiceRepository.save(inv);

        notificationService.create(c.getTenantUserId(), "INVOICE_NEW",
                "Hóa đơn tháng " + month + "/" + year,
                "Mã: " + inv.getCode() + " · Tổng " + MoneyFormatter.vnd(total) + " · Hạn " + dueDate,
                "/me/invoices");
        automationEmailService.sendInvoiceIssued(inv);
        return inv;
    }

    /**
     * Bulk-generate invoices for ALL active contracts of an owner for a given period.
     * Skips contracts that already have an invoice for that period.
     * Uses zero electric/water consumption — owner edits readings later via single-edit.
     * Returns number of invoices created.
     */
    @Transactional
    public int batchGenerate(UUID ownerId, short year, short month) {
        var contracts = contractRepository.findByOwnerIdAndStatus(ownerId,
                Contract.ContractStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        int created = 0;
        for (Contract c : contracts) {
            if (invoiceRepository.existsByContractIdAndPeriodYearAndPeriodMonth(c.getId(), year, month)) {
                continue;
            }
            String code = nextSequentialInvoiceCode();

            BigDecimal total = c.getRentMonthly()
                    .add(c.getServiceFee())
                    .add(sumRoomExtras(c.getRoomId()));

            Invoice inv = Invoice.builder()
                    .code(code)
                    .contractId(c.getId())
                    .ownerId(ownerId)
                    .tenantUserId(c.getTenantUserId())
                    .roomId(c.getRoomId())
                    .periodYear(year)
                    .periodMonth(month)
                    .issueDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(10))
                    .rentAmount(c.getRentMonthly())
                    .serviceAmount(c.getServiceFee())
                    .electricAmount(BigDecimal.ZERO)
                    .waterAmount(BigDecimal.ZERO)
                    .otherAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(total)
                    .paidAmount(BigDecimal.ZERO)
                    .status(Invoice.InvoiceStatus.PENDING)
                    .build();
            invoiceRepository.save(inv);

            notificationService.create(c.getTenantUserId(), "INVOICE_NEW",
                    "Hóa đơn tháng " + month + "/" + year,
                    "Hóa đơn mới phát hành: " + MoneyFormatter.vnd(total) + ". Hãy cập nhật chỉ số điện/nước.",
                    "/me/invoices");
            automationEmailService.sendInvoiceIssued(inv);
            created++;
        }
        return created;
    }

    @Transactional
    public Invoice markPaid(UUID ownerId, UUID id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        inv.setPaidAmount(inv.getTotalAmount());
        inv.setStatus(Invoice.InvoiceStatus.PAID);
        inv.setPaidAt(OffsetDateTime.now());
        inv = invoiceRepository.save(inv);
        extendRoomPaidUntil(inv);
        return inv;
    }

    @Transactional
    public Invoice markPending(UUID ownerId, UUID id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setPaidAt(null);
        inv.setStatus(Invoice.InvoiceStatus.PENDING);
        inv = invoiceRepository.save(inv);
        recalculateRoomPaidUntilAfterUnpay(inv);
        return inv;
    }

    @Transactional
    public Invoice cancel(UUID ownerId, UUID id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        if (inv.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            return inv;
        }
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setPaidAt(null);
        inv.setStatus(Invoice.InvoiceStatus.CANCELLED);
        inv = invoiceRepository.save(inv);
        recalculateRoomPaidUntilAfterUnpay(inv);
        return inv;
    }

    @Transactional
    public int refreshAgingStatuses() {
        return refreshAgingStatuses(null);
    }

    @Transactional
    public int refreshAgingStatuses(UUID ownerId) {
        LocalDate today = LocalDate.now();
        LocalDate cancelCutoff = today.minusDays(10);
        int updated = 0;

        List<Invoice> toCancel = ownerId != null
                ? invoiceRepository.findByOwnerIdAndStatusInAndDueDateLessThanEqual(ownerId, AUTO_CANCEL_STATUSES, cancelCutoff)
                : invoiceRepository.findByStatusInAndDueDateLessThanEqual(AUTO_CANCEL_STATUSES, cancelCutoff);
        for (Invoice invoice : toCancel) {
            if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED || invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
                continue;
            }
            invoice.setPaidAmount(BigDecimal.ZERO);
            invoice.setPaidAt(null);
            invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
            updated++;
        }
        if (!toCancel.isEmpty()) {
            invoiceRepository.saveAll(toCancel);
        }

        List<Invoice> toOverdue = ownerId != null
                ? invoiceRepository.findByOwnerIdAndStatusAndDueDateBefore(ownerId, Invoice.InvoiceStatus.PENDING, today)
                : invoiceRepository.findByStatusAndDueDateBefore(Invoice.InvoiceStatus.PENDING, today);
        for (Invoice invoice : toOverdue) {
            if (invoice.getDueDate() != null && invoice.getDueDate().isAfter(cancelCutoff)) {
                invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
                updated++;
            }
        }
        if (!toOverdue.isEmpty()) {
            invoiceRepository.saveAll(toOverdue);
        }
        return updated;
    }

    @Transactional
    public Invoice updatePaymentStatus(UUID ownerId, UUID id, String statusValue) {
        Invoice.InvoiceStatus status;
        try {
            status = Invoice.InvoiceStatus.valueOf(statusValue == null ? "" : statusValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Trạng thái hóa đơn không hợp lệ.");
        }

        if (status == Invoice.InvoiceStatus.PAID) {
            return markPaid(ownerId, id);
        }
        if (status == Invoice.InvoiceStatus.PENDING) {
            return markPending(ownerId, id);
        }
        if (status == Invoice.InvoiceStatus.CANCELLED) {
            return cancel(ownerId, id);
        }
        throw BusinessException.badRequest("Trang này chỉ hỗ trợ đổi trạng thái thanh toán hoặc hủy hóa đơn.");
    }

    @Transactional
    public Invoice sendReminder(UUID ownerId, UUID id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        if (inv.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.conflict("Hóa đơn đã được thanh toán.");
        }
        notificationService.create(inv.getTenantUserId(), "INVOICE_REMINDER",
                "Nhắc thanh toán hóa đơn " + inv.getCode(),
                "Hạn thanh toán: " + inv.getDueDate() + " · Tổng " + MoneyFormatter.vnd(inv.getTotalAmount()),
                "/me/invoices");
        automationEmailService.sendPaymentReminder(inv);
        inv.setLastReminderAt(OffsetDateTime.now());
        return invoiceRepository.save(inv);
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!inv.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hóa đơn này.");
        }
        if (inv.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.conflict("Không thể xóa hóa đơn đã thanh toán. Hủy thay vì xóa.");
        }
        invoiceRepository.delete(inv);
    }

    private BigDecimal computeAmount(BigDecimal prev, BigDecimal curr, BigDecimal unitPrice) {
        if (prev == null || curr == null) return BigDecimal.ZERO;
        BigDecimal qty = curr.subtract(prev);
        if (qty.signum() <= 0) return BigDecimal.ZERO;
        return qty.multiply(unitPrice);
    }

    private String nextSequentialInvoiceCode() {
        int next = invoiceRepository.findMaxSequentialInvoiceCode() + 1;
        for (int i = 0; i < 1000; i++) {
            String code = "PT-HD-" + (next + i);
            if (!invoiceRepository.existsByCode(code)) {
                return code;
            }
        }
        return "PT-HD-" + System.currentTimeMillis();
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static String cleanSearch(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.isBlank()
                || "q".equalsIgnoreCase(clean)
                || "filterQ".equalsIgnoreCase(clean)) {
            return "";
        }
        return clean;
    }

    public List<InvoiceLineItem> parseOtherItems(Invoice invoice) {
        if (invoice == null || invoice.getOtherItemsJson() == null || invoice.getOtherItemsJson().isBlank()) {
            return List.of();
        }
        try {
            List<InvoiceLineItem> parsed = objectMapper.readValue(
                    invoice.getOtherItemsJson(), new TypeReference<List<InvoiceLineItem>>() {});
            return normalizeLineItems(parsed);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private BigDecimal sumLineItems(List<InvoiceLineItem> items) {
        return normalizeLineItems(items).stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<InvoiceLineItem> normalizeLineItems(List<InvoiceLineItem> items) {
        List<InvoiceLineItem> normalized = new ArrayList<>();
        if (items == null) return normalized;
        for (InvoiceLineItem item : items) {
            if (item == null || item.getAmount() == null || item.getAmount().signum() <= 0) continue;
            String name = item.getName() == null || item.getName().isBlank() ? "Phí khác" : item.getName().trim();
            normalized.add(new InvoiceLineItem(name, item.getAmount()));
        }
        return normalized;
    }

    private String writeOtherItems(List<InvoiceLineItem> items) {
        List<InvoiceLineItem> normalized = normalizeLineItems(items);
        if (normalized.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void extendRoomPaidUntil(Invoice inv) {
        if (inv.getRoomId() == null || inv.getTenantUserId() == null
                || inv.getPeriodYear() == null || inv.getPeriodMonth() == null) {
            return;
        }
        roomRepository.findById(inv.getRoomId()).ifPresent(room -> {
            if (!inv.getOwnerId().equals(room.getOwnerId())) return;
            if (room.getCurrentTenantId() == null || !room.getCurrentTenantId().equals(inv.getTenantUserId())) return;

            if (room.getCurrentTenantStartedOn() == null) {
                room.setCurrentTenantStartedOn(LocalDate.now());
            }
            LocalDate baseDate = room.getCurrentTenantPaidUntil() != null
                    ? room.getCurrentTenantPaidUntil()
                    : YearMonth.of(inv.getPeriodYear(), inv.getPeriodMonth()).atEndOfMonth();
            LocalDate extendedUntil = baseDate.plusMonths(1);
            if (room.getCurrentTenantPaidUntil() == null || room.getCurrentTenantPaidUntil().isBefore(extendedUntil)) {
                room.setCurrentTenantPaidUntil(extendedUntil);
                roomRepository.save(room);
            }
        });
    }

    private void recalculateRoomPaidUntilAfterUnpay(Invoice inv) {
        if (inv.getRoomId() == null || inv.getTenantUserId() == null
                || inv.getPeriodYear() == null || inv.getPeriodMonth() == null) return;
        roomRepository.findById(inv.getRoomId()).ifPresent(room -> {
            if (!inv.getOwnerId().equals(room.getOwnerId())) return;
            if (room.getCurrentTenantId() == null || !room.getCurrentTenantId().equals(inv.getTenantUserId())) return;

            LocalDate revertedPeriodEnd = YearMonth.of(inv.getPeriodYear(), inv.getPeriodMonth()).atEndOfMonth();
            LocalDate revertedCoverageEnd = revertedPeriodEnd.plusMonths(1);
            if (room.getCurrentTenantPaidUntil() == null
                    || (!room.getCurrentTenantPaidUntil().equals(revertedPeriodEnd)
                    && !room.getCurrentTenantPaidUntil().equals(revertedCoverageEnd))) {
                return;
            }

            var latestPaid = invoiceRepository
                    .findByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByPeriodYearDescPeriodMonthDescCreatedAtDesc(
                            inv.getOwnerId(), inv.getRoomId(), inv.getTenantUserId(), Invoice.InvoiceStatus.PAID)
                    .stream()
                    .findFirst();
            room.setCurrentTenantPaidUntil(latestPaid
                    .map(i -> YearMonth.of(i.getPeriodYear(), i.getPeriodMonth()).atEndOfMonth().plusMonths(1))
                    .orElse(null));
            roomRepository.save(room);
        });
    }

    /**
     * Effective electric/water unit prices for a contract.
     * Priority: contract snapshot → room (override or inherited) → property defaults.
     */
    private BigDecimal[] resolveTariff(Contract c) {
        BigDecimal electric = c.getElectricUnit();
        BigDecimal water    = c.getWaterUnit();
        if (electric != null && water != null && electric.signum() > 0 && water.signum() > 0) {
            return new BigDecimal[]{electric, water};
        }
        Room r = c.getRoomId() != null ? roomRepository.findById(c.getRoomId()).orElse(null) : null;
        if (r != null) {
            if (r.isInheritTariff()) {
                Property p = propertyRepository.findById(r.getPropertyId()).orElse(null);
                if (p != null) {
                    if (electric == null || electric.signum() == 0) electric = p.getElectricUnit();
                    if (water    == null || water.signum() == 0)    water    = p.getWaterUnit();
                }
            } else {
                if (electric == null || electric.signum() == 0) electric = r.getElectricUnit();
                if (water    == null || water.signum() == 0)    water    = r.getWaterUnit();
            }
        }
        return new BigDecimal[]{nz(electric), nz(water)};
    }

    /** Sum of room-level extra fees (parking, elevator, internet, etc.). */
    private BigDecimal sumRoomExtras(UUID roomId) {
        if (roomId == null) return BigDecimal.ZERO;
        Room r = roomRepository.findById(roomId).orElse(null);
        if (r == null || r.getExtraFees() == null) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (var fee : r.getExtraFees()) {
            if (fee.getAmount() != null) sum = sum.add(fee.getAmount());
        }
        return sum;
    }

    /** Used by templates to suggest current period (for batch dialog). */
    public YearMonth currentPeriod() { return YearMonth.now(); }
}
