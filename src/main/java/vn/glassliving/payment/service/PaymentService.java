package vn.glassliving.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;

    @Transactional
    public Payment createInvoicePayment(UUID userId, UUID invoiceId, String methodValue) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!userId.equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền thanh toán hóa đơn này.");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("Hóa đơn đã hủy không thể thanh toán.");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.conflict("Hóa đơn này đã được thanh toán.");
        }

        BigDecimal amount = remainingAmount(invoice);
        if (amount.signum() <= 0) {
            throw BusinessException.conflict("Hóa đơn không còn số tiền phải thanh toán.");
        }

        Payment payment = Payment.builder()
                .code(nextCode())
                .invoiceId(invoice.getId())
                .userId(userId)
                .amount(amount)
                .method(parseMethod(methodValue))
                .status(Payment.PaymentStatus.PENDING)
                .note("Thanh toán hóa đơn " + invoice.getCode())
                .gatewayPayload("{\"source\":\"customer_invoice_checkout\",\"invoiceCode\":\"" + invoice.getCode() + "\"}")
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getOwned(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> BusinessException.notFound("Thanh toán"));
    }

    @Transactional
    public Payment completeInvoicePayment(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> BusinessException.notFound("Thanh toán"));
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
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
        if (!userId.equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền thanh toán hóa đơn này.");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("Hóa đơn đã hủy không thể thanh toán.");
        }
        if (invoice.getStatus() != Invoice.InvoiceStatus.PAID) {
            invoiceService.markPaid(invoice.getOwnerId(), invoice.getId());
        }

        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setGatewayTxnId("AUTO-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        payment.setGatewayPayload("{\"source\":\"auto_checkout\",\"status\":\"SUCCESS\"}");
        Payment saved = paymentRepository.save(payment);

        notificationService.create(invoice.getOwnerId(), "PAYMENT_SUCCESS",
                "Khách đã thanh toán " + invoice.getCode(),
                "Số tiền: " + MoneyFormatter.vnd(payment.getAmount()),
                "/admin/invoices/" + invoice.getId());
        notificationService.create(userId, "PAYMENT_SUCCESS",
                "Thanh toán thành công",
                "Hóa đơn " + invoice.getCode() + " đã được ghi nhận.",
                "/me/invoices/" + invoice.getId());
        return saved;
    }

    public BigDecimal remainingAmount(Invoice invoice) {
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(paid);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    private Payment.PaymentMethod parseMethod(String value) {
        String normalized = value == null || value.isBlank() ? "VNPAY" : value.trim().toUpperCase();
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
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
