package vn.glassliving.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class BankTransferVerificationService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RestClient restClient;
    private final String historyUrl;
    private final BankInfo bankInfo;

    public BankTransferVerificationService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            RestClient.Builder restClientBuilder,
            @Value("${app.payments.bank-history-url:}") String historyUrl,
            @Value("${app.payments.bank-name:ACB}") String bankName,
            @Value("${app.payments.bank-account-name:SMARTRENT}") String accountName,
            @Value("${app.payments.bank-account-no:}") String accountNo) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.historyUrl = clean(historyUrl);
        this.bankInfo = new BankInfo(bankName, accountName, accountNo);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public BankInfo bankInfo() {
        return bankInfo;
    }

    public String transferContent(Invoice invoice) {
        if (invoice == null) return "SMARTRENT";
        return paymentService.buildPaymentCode(invoice);
    }

    @Transactional
    public CheckResult checkInvoice(UUID userId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!userId.equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền kiểm tra hóa đơn này.");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return new CheckResult("PAID", "Hóa đơn đã được thanh toán.", invoice.getPaidAt(), null);
        }

        Payment payment = paymentRepository
                .findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                        invoiceId, userId, Payment.PaymentStatus.PENDING)
                .orElse(null);
        if (payment == null) {
            return new CheckResult(
                    "PENDING",
                    "Chưa có phiên thanh toán đang chờ. Vui lòng mở lại trang thanh toán.",
                    null,
                    null);
        }
        if (historyUrl == null) {
            return new CheckResult(
                    "UNAVAILABLE",
                    "Đối soát tự động chưa được cấu hình. Vui lòng liên hệ chủ trọ.",
                    null,
                    null);
        }

        String expectedContent = normalize(transferContent(invoice));
        try {
            JsonNode response = restClient.get()
                    .uri(historyUrl)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode transactions = response != null ? response.path("data") : null;
            if (transactions != null && transactions.isArray()) {
                for (JsonNode transaction : transactions) {
                    if (!"IN".equalsIgnoreCase(transaction.path("type").asText())) continue;

                    BigDecimal amount = transaction.path("amount").decimalValue();
                    if (amount.compareTo(payment.getAmount()) != 0) continue;

                    String description = normalize(transaction.path("description").asText());
                    if (!description.contains(expectedContent)) continue;

                    String transactionNumber = transaction.path("transactionNumber").asText();
                    if (transactionNumber.isBlank()) continue;
                    String gatewayTxnId = "ACB:" + transactionNumber;
                    if (paymentRepository.existsByGatewayTxnIdAndStatus(
                            gatewayTxnId, Payment.PaymentStatus.SUCCESS)
                            || paymentRepository.existsByGatewayTxnIdAndStatus(
                            gatewayTxnId, Payment.PaymentStatus.PAID)) {
                        continue;
                    }

                    Payment completed = paymentService.completeVerifiedBankTransfer(
                            userId,
                            payment.getId(),
                            gatewayTxnId,
                            "{\"source\":\"acb_history\",\"verified\":true}");
                    return new CheckResult(
                            "PAID",
                            "Đã tìm thấy giao dịch và cập nhật hóa đơn.",
                            completed.getPaidAt(),
                            gatewayTxnId);
                }
            }
            return new CheckResult(
                    "PENDING",
                    "Chưa thấy giao dịch phù hợp. Vui lòng chờ 1-3 phút rồi kiểm tra lại.",
                    null,
                    null);
        } catch (RestClientException ex) {
            return new CheckResult(
                    "ERROR",
                    "Chưa thể kết nối ngân hàng. Vui lòng thử lại sau.",
                    null,
                    null);
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }

    public record BankInfo(String bankName, String accountName, String accountNo) {}

    public record CheckResult(
            String status,
            String message,
            OffsetDateTime paidAt,
            String transactionId) {}
}
