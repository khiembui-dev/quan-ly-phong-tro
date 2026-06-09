package vn.glassliving.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.dto.BankHistoryResponseDto;
import vn.glassliving.payment.dto.BankTransactionDto;
import vn.glassliving.payment.dto.PaymentCheckResponseDto;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<Payment.PaymentStatus> PAID_STATUSES = List.of(
            Payment.PaymentStatus.PAID,
            Payment.PaymentStatus.SUCCESS
    );

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final RoomRepository roomRepository;
    private final BankHistoryClient bankHistoryClient;
    private final PaymentBankProperties bankProperties;

    @Transactional
    public Payment createInvoicePayment(UUID userId, UUID invoiceId, String methodValue) {
        return getOrCreatePaymentForInvoice(invoiceId, userId, parseMethod(methodValue));
    }

    @Transactional
    public Payment getOrCreatePaymentForInvoice(UUID invoiceId, UUID customerId) {
        return getOrCreatePaymentForInvoice(invoiceId, customerId, Payment.PaymentMethod.BANK_TRANSFER);
    }

    @Transactional(readOnly = true)
    public Payment getOwned(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> BusinessException.notFound("Thanh toán"));
    }

    @Transactional
    public Payment completeInvoicePayment(UUID userId, UUID paymentId) {
        throw BusinessException.conflict(
                "Thanh toán chỉ được xác nhận sau khi hệ thống đối soát giao dịch ngân hàng.");
    }

    @Transactional
    public Payment completeVerifiedBankTransfer(UUID userId,
                                                UUID paymentId,
                                                String gatewayTxnId,
                                                String gatewayPayload) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> BusinessException.notFound("Thanh toán"));
        if (isPaid(payment.getStatus())) {
            return payment;
        }
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw BusinessException.conflict("Phiên thanh toán không còn ở trạng thái chờ.");
        }
        if (payment.getInvoiceId() == null) {
            throw BusinessException.badRequest("Phiên thanh toán không gắn với hóa đơn.");
        }

        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        validateCustomerInvoice(invoice, userId, true);

        String transactionNumber = bankTransactionNumber(gatewayTxnId);
        BankTransactionDto transaction = new BankTransactionDto(
                payment.getAmount(),
                null,
                null,
                transactionNumber,
                gatewayPayload,
                "ACB",
                "IN",
                "VND",
                null,
                null);
        return markInvoiceAsPaid(invoice, payment, transaction);
    }

    public String buildPaymentCode(UUID invoiceId, UUID customerId) {
        return "PT-" + shortInvoiceIdentifier(invoiceId);
    }

    public String buildPaymentCode(Invoice invoice) {
        return buildPaymentCode(invoice, invoice != null ? invoice.getTenantUserId() : null);
    }

    @Transactional
    public PaymentCheckResponseDto checkInvoicePayment(UUID invoiceId, UUID currentCustomer) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        validateCustomerInvoice(invoice, currentCustomer, false);

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return PaymentCheckResponseDto.paid(invoice.getTotalAmount(), invoice.getPaidAt());
        }

        Payment payment = getOrCreatePaymentForInvoice(invoice, currentCustomer, Payment.PaymentMethod.BANK_TRANSFER);
        Optional<BankHistoryResponseDto> history = bankHistoryClient.fetchHistory();
        if (history.isEmpty()) {
            return PaymentCheckResponseDto.bankApiError();
        }
        BankHistoryResponseDto response = history.get();
        if (response.codeStatus() == null || response.codeStatus() != 200) {
            return PaymentCheckResponseDto.bankApiError();
        }

        return matchBankTransaction(invoice, payment.getPaymentCode(), response)
                .map(transaction -> markInvoiceAsPaid(invoice, payment, transaction))
                .map(completed -> PaymentCheckResponseDto.paid(completed.getAmount(), completed.getPaidAt()))
                .orElseGet(PaymentCheckResponseDto::pending);
    }

    public Optional<BankTransactionDto> matchBankTransaction(Invoice invoice,
                                                            String paymentCode,
                                                            BankHistoryResponseDto response) {
        if (invoice == null || response == null || response.data() == null || paymentCode == null) {
            return Optional.empty();
        }
        BigDecimal expectedAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        String expectedCode = normalize(paymentCode);
        if (expectedAmount.signum() <= 0 || expectedCode.isBlank()) {
            return Optional.empty();
        }

        for (BankTransactionDto transaction : response.data()) {
            if (transaction == null) continue;
            if (!"IN".equalsIgnoreCase(clean(transaction.type()))) continue;
            if (!"VND".equalsIgnoreCase(clean(transaction.currency()))) continue;
            if (transaction.amount() == null || transaction.amount().compareTo(expectedAmount) != 0) continue;
            if (!normalize(transaction.description()).contains(expectedCode)) continue;
            String transactionNumber = clean(transaction.transactionNumber());
            if (transactionNumber == null) continue;
            if (isRecordedBankTransaction(transactionNumber, gatewayTransactionId(transaction))) continue;
            return Optional.of(transaction);
        }
        return Optional.empty();
    }

    @Transactional
    public Payment markInvoiceAsPaid(Invoice invoice, Payment payment, BankTransactionDto transaction) {
        if (invoice == null || payment == null || transaction == null) {
            throw BusinessException.badRequest("Thiếu dữ liệu thanh toán.");
        }
        validateCustomerInvoice(invoice, payment.getUserId(), true);

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return payment;
        }

        OffsetDateTime paidAt = OffsetDateTime.now();
        invoice.setPaidAmount(invoice.getTotalAmount());
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoice.setPaidAt(paidAt);
        if (invoice.getPaymentCode() == null || invoice.getPaymentCode().isBlank()) {
            invoice.setPaymentCode(payment.getPaymentCode());
        }
        invoiceRepository.save(invoice);

        payment.setAmount(invoice.getTotalAmount());
        payment.setMethod(Payment.PaymentMethod.BANK_TRANSFER);
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setPaymentCode(payment.getPaymentCode() != null ? payment.getPaymentCode() : invoice.getPaymentCode());
        payment.setBankTransactionNumber(clean(transaction.transactionNumber()));
        payment.setBankName(resolveBankName(transaction));
        payment.setBankDescription(transaction.description());
        payment.setGatewayTxnId(gatewayTransactionId(transaction));
        payment.setGatewayPayload(bankPayload(transaction));
        Payment saved = paymentRepository.save(payment);

        extendRoomUsageAfterPaid(invoice);
        notifyPaymentSuccess(invoice, saved);
        return saved;
    }

    @Transactional
    public void extendRoomUsageAfterPaid(Invoice invoice) {
        if (invoice == null || invoice.getRoomId() == null || invoice.getTenantUserId() == null
                || invoice.getPeriodYear() == null || invoice.getPeriodMonth() == null) {
            return;
        }
        roomRepository.findById(invoice.getRoomId()).ifPresent(room -> {
            if (!invoice.getOwnerId().equals(room.getOwnerId())) return;
            if (room.getCurrentTenantId() == null || !room.getCurrentTenantId().equals(invoice.getTenantUserId())) return;

            if (room.getCurrentTenantStartedOn() == null) {
                room.setCurrentTenantStartedOn(LocalDate.now());
            }
            LocalDate baseDate = room.getCurrentTenantPaidUntil() != null
                    ? room.getCurrentTenantPaidUntil()
                    : YearMonth.of(invoice.getPeriodYear(), invoice.getPeriodMonth()).atEndOfMonth();
            LocalDate extendedUntil = baseDate.plusMonths(1);
            if (room.getCurrentTenantPaidUntil() == null
                    || room.getCurrentTenantPaidUntil().isBefore(extendedUntil)) {
                room.setCurrentTenantPaidUntil(extendedUntil);
                roomRepository.save(room);
            }
        });
    }

    public BigDecimal remainingAmount(Invoice invoice) {
        BigDecimal total = invoice != null && invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice != null && invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(paid);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    private Payment getOrCreatePaymentForInvoice(UUID invoiceId, UUID customerId, Payment.PaymentMethod method) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        validateCustomerInvoice(invoice, customerId, false);
        return getOrCreatePaymentForInvoice(invoice, customerId, method);
    }

    private Payment getOrCreatePaymentForInvoice(Invoice invoice, UUID customerId, Payment.PaymentMethod method) {
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusInOrderByCreatedAtDesc(
                            invoice.getId(), customerId, PAID_STATUSES)
                    .or(() -> paymentRepository.findFirstByInvoiceIdAndUserIdOrderByCreatedAtDesc(invoice.getId(), customerId))
                    .orElseGet(() -> displayOnlyPaidPayment(invoice, customerId));
        }

        BigDecimal amount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            throw BusinessException.conflict("Hóa đơn không còn số tiền phải thanh toán.");
        }

        String paymentCode = buildPaymentCode(invoice, customerId);
        if (!paymentCode.equals(invoice.getPaymentCode())) {
            invoice.setPaymentCode(paymentCode);
            invoiceRepository.save(invoice);
        }

        Optional<Payment> existing = paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoice.getId(), customerId, Payment.PaymentStatus.PENDING);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            boolean changed = false;
            if (payment.getAmount() == null || payment.getAmount().compareTo(amount) != 0) {
                payment.setAmount(amount);
                changed = true;
            }
            if (payment.getMethod() != method) {
                payment.setMethod(method);
                changed = true;
            }
            if (!paymentCode.equals(payment.getPaymentCode())) {
                payment.setPaymentCode(paymentCode);
                changed = true;
            }
            return changed ? paymentRepository.save(payment) : payment;
        }

        Payment payment = Payment.builder()
                .code(nextCode())
                .invoiceId(invoice.getId())
                .userId(customerId)
                .amount(amount)
                .method(method)
                .paymentCode(paymentCode)
                .status(Payment.PaymentStatus.PENDING)
                .note("Thanh toán hóa đơn " + invoice.getCode())
                .gatewayPayload("{\"source\":\"customer_invoice_checkout\",\"invoiceCode\":\"" + escapeJson(invoice.getCode()) + "\"}")
                .build();
        return paymentRepository.save(payment);
    }

    private void validateCustomerInvoice(Invoice invoice, UUID customerId, boolean allowPaid) {
        if (!customerId.equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền thanh toán hóa đơn này.");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("Hóa đơn đã hủy không thể thanh toán.");
        }
        if (!allowPaid && invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return;
        }
    }

    private Payment displayOnlyPaidPayment(Invoice invoice, UUID customerId) {
        Payment payment = Payment.builder()
                .code("PAY-" + invoice.getCode())
                .invoiceId(invoice.getId())
                .userId(customerId)
                .amount(invoice.getTotalAmount())
                .method(Payment.PaymentMethod.BANK_TRANSFER)
                .paymentCode(invoice.getPaymentCode() != null ? invoice.getPaymentCode() : buildPaymentCode(invoice, customerId))
                .status(Payment.PaymentStatus.PAID)
                .paidAt(invoice.getPaidAt())
                .build();
        return payment;
    }

    private boolean isRecordedBankTransaction(String transactionNumber, String gatewayTxnId) {
        for (Payment.PaymentStatus status : PAID_STATUSES) {
            if (paymentRepository.existsByBankTransactionNumberAndStatus(transactionNumber, status)) {
                return true;
            }
            if (gatewayTxnId != null && paymentRepository.existsByGatewayTxnIdAndStatus(gatewayTxnId, status)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPaid(Payment.PaymentStatus status) {
        return status == Payment.PaymentStatus.PAID || status == Payment.PaymentStatus.SUCCESS;
    }

    private Payment.PaymentMethod parseMethod(String value) {
        String normalized = value == null || value.isBlank() ? "BANK_TRANSFER" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return Payment.PaymentMethod.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Phương thức thanh toán không hợp lệ.");
        }
    }

    private String nextCode() {
        for (int i = 0; i < 8; i++) {
            String code = "PAY-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"
                    + String.format("%04d", (int) (Math.random() * 9000) + 1000);
            if (!paymentRepository.existsByCode(code)) return code;
        }
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String gatewayTransactionId(BankTransactionDto transaction) {
        String transactionNumber = clean(transaction.transactionNumber());
        if (transactionNumber == null) return null;
        String bank = clean(transaction.bankName());
        return (bank != null ? bank.toUpperCase(Locale.ROOT) : "ACB") + ":" + transactionNumber;
    }

    private String resolveBankName(BankTransactionDto transaction) {
        String bankName = clean(transaction != null ? transaction.bankName() : null);
        if (bankName != null) {
            return bankName;
        }
        if (bankProperties != null) {
            String configured = clean(bankProperties.getCode());
            if (configured != null) {
                return configured;
            }
            configured = clean(bankProperties.getDisplayName());
            if (configured != null) {
                return configured;
            }
        }
        return "ACB";
    }

    private String bankTransactionNumber(String gatewayTxnId) {
        String clean = clean(gatewayTxnId);
        if (clean == null) return null;
        int colon = clean.indexOf(':');
        return colon >= 0 ? clean.substring(colon + 1) : clean;
    }

    private String bankPayload(BankTransactionDto transaction) {
        return "{\"source\":\"acb_history\",\"verified\":true"
                + ",\"transactionNumber\":\"" + escapeJson(transaction.transactionNumber()) + "\""
                + ",\"bankName\":\"" + escapeJson(transaction.bankName()) + "\""
                + ",\"description\":\"" + escapeJson(transaction.description()) + "\""
                + "}";
    }

    private void notifyPaymentSuccess(Invoice invoice, Payment payment) {
        if (notificationService == null) {
            return;
        }
        notificationService.create(invoice.getOwnerId(), "PAYMENT_SUCCESS",
                "Khách đã thanh toán " + invoice.getCode(),
                "Số tiền: " + MoneyFormatter.vnd(payment.getAmount()),
                "/admin/invoices/" + invoice.getId());
        notificationService.create(payment.getUserId(), "PAYMENT_SUCCESS",
                "Thanh toán thành công",
                "Hóa đơn " + invoice.getCode() + " đã được ghi nhận.",
                "/me/invoices/" + invoice.getId());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String buildPaymentCode(Invoice invoice, UUID customerId) {
        String invoiceCode = clean(invoice != null ? invoice.getCode() : null);
        if (invoiceCode != null) {
            String normalized = invoiceCode.toUpperCase(Locale.ROOT);
            if (normalized.matches("PT-HD-\\d+")) {
                return "PT-" + normalized.substring("PT-HD-".length());
            }
            if (normalized.matches("PT-\\d+")) {
                return normalized;
            }
        }
        return buildPaymentCode(invoice != null ? invoice.getId() : null, customerId);
    }

    private String shortInvoiceIdentifier(UUID invoiceId) {
        if (invoiceId == null) {
            return "UNKNOWN";
        }
        if (invoiceId.getMostSignificantBits() == 0L && invoiceId.getLeastSignificantBits() > 0L) {
            return Long.toUnsignedString(invoiceId.getLeastSignificantBits());
        }
        String compact = invoiceId.toString().replace("-", "");
        return compact.substring(0, Math.min(8, compact.length())).toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
