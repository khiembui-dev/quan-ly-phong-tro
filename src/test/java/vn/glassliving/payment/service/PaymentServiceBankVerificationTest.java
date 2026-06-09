package vn.glassliving.payment.service;

import org.junit.jupiter.api.Test;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import org.springframework.web.client.RestClient;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.dto.BankHistoryResponseDto;
import vn.glassliving.payment.dto.BankTransactionDto;
import vn.glassliving.payment.dto.PaymentCheckResponseDto;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceBankVerificationTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final PaymentBankProperties bankProperties = new PaymentBankProperties();
    private final StubBankHistoryClient bankHistoryClient = new StubBankHistoryClient(bankProperties);
    private final PaymentService paymentService = new PaymentService(
            paymentRepository,
            invoiceRepository,
            null,
            roomRepository,
            bankHistoryClient,
            bankProperties
    );

    @Test
    void checkInvoicePaymentMarksPaidWhenIncomingVndTransactionMatchesAmountAndPaymentCode() {
        UUID invoiceId = new UUID(0L, 1L);
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String paymentCode = paymentService.buildPaymentCode(invoiceId, customerId);
        Invoice invoice = invoice(invoiceId, customerId, ownerId, roomId, new BigDecimal("850000"));
        invoice.setPaymentCode(paymentCode);
        Payment payment = payment(invoiceId, customerId, new BigDecimal("850000"), paymentCode);
        Room room = room(roomId, ownerId, customerId);
        BankTransactionDto transaction = transaction("47976", new BigDecimal("850000"), "IN", "VND",
                "Thanh toan " + paymentCode + " tien phong", "ACB");

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoiceId, customerId, Payment.PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        bankHistoryClient.response = Optional.of(new BankHistoryResponseDto(200, "success", List.of(transaction)));
        when(paymentRepository.existsByBankTransactionNumberAndStatus("47976", Payment.PaymentStatus.PAID)).thenReturn(false);
        when(paymentRepository.existsByGatewayTxnIdAndStatus("ACB:47976", Payment.PaymentStatus.PAID)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCheckResponseDto response = paymentService.checkInvoicePayment(invoiceId, customerId);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.amount()).isEqualByComparingTo("850000");
        assertThat(response.paidAt()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("850000");
        assertThat(invoice.getPaidAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(payment.getMethod()).isEqualTo(Payment.PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getPaymentCode()).isEqualTo(paymentCode);
        assertThat(payment.getBankTransactionNumber()).isEqualTo("47976");
        assertThat(payment.getGatewayTxnId()).isEqualTo("ACB:47976");
        assertThat(payment.getBankName()).isEqualTo("ACB");
        assertThat(payment.getBankDescription()).contains(paymentCode);
        assertThat(paymentCode).isEqualTo("PT-1");
        assertThat(room.getCurrentTenantPaidUntil()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void checkInvoicePaymentDoesNotReuseAlreadyRecordedBankTransaction() {
        UUID invoiceId = new UUID(0L, 2L);
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String paymentCode = paymentService.buildPaymentCode(invoiceId, customerId);
        Invoice invoice = invoice(invoiceId, customerId, ownerId, roomId, new BigDecimal("850000"));
        invoice.setPaymentCode(paymentCode);
        Payment payment = payment(invoiceId, customerId, new BigDecimal("850000"), paymentCode);
        BankTransactionDto transaction = transaction("47976", new BigDecimal("850000"), "IN", "VND",
                paymentCode, "ACB");

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoiceId, customerId, Payment.PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        bankHistoryClient.response = Optional.of(new BankHistoryResponseDto(200, "success", List.of(transaction)));
        when(paymentRepository.existsByBankTransactionNumberAndStatus("47976", Payment.PaymentStatus.PAID)).thenReturn(true);

        PaymentCheckResponseDto response = paymentService.checkInvoicePayment(invoiceId, customerId);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void checkInvoicePaymentFallsBackToConfiguredBankCodeWhenApiBankNameIsBlank() {
        UUID invoiceId = new UUID(0L, 4L);
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String paymentCode = paymentService.buildPaymentCode(invoiceId, customerId);
        Invoice invoice = invoice(invoiceId, customerId, ownerId, roomId, new BigDecimal("12000"));
        invoice.setPaymentCode(paymentCode);
        Payment payment = payment(invoiceId, customerId, new BigDecimal("12000"), paymentCode);
        BankTransactionDto transaction = transaction("47981", new BigDecimal("12000"), "IN", "VND",
                "PT4 GD 6159MSCBD2ZKAB2X", "");

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoiceId, customerId, Payment.PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        bankHistoryClient.response = Optional.of(new BankHistoryResponseDto(200, "success", List.of(transaction)));
        when(paymentRepository.existsByBankTransactionNumberAndStatus("47981", Payment.PaymentStatus.PAID)).thenReturn(false);
        when(paymentRepository.existsByGatewayTxnIdAndStatus("ACB:47981", Payment.PaymentStatus.PAID)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        PaymentCheckResponseDto response = paymentService.checkInvoicePayment(invoiceId, customerId);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(payment.getBankName()).isEqualTo("ACB");
        assertThat(payment.getBankTransactionNumber()).isEqualTo("47981");
        assertThat(payment.getGatewayTxnId()).isEqualTo("ACB:47981");
    }

    @Test
    void buildPaymentCodeUsesShortReadableInvoiceNumberOnly() {
        UUID customerId = UUID.randomUUID();

        assertThat(paymentService.buildPaymentCode(new UUID(0L, 1L), customerId)).isEqualTo("PT-1");
        assertThat(paymentService.buildPaymentCode(new UUID(0L, 42L), customerId)).isEqualTo("PT-42");
    }

    @Test
    void checkInvoicePaymentDoesNotMatchOldUuidStyleCode() {
        UUID invoiceId = new UUID(0L, 3L);
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String paymentCode = paymentService.buildPaymentCode(invoiceId, customerId);
        Invoice invoice = invoice(invoiceId, customerId, ownerId, roomId, new BigDecimal("850000"));
        invoice.setPaymentCode(paymentCode);
        Payment payment = payment(invoiceId, customerId, new BigDecimal("850000"), paymentCode);
        BankTransactionDto transaction = transaction("47977", new BigDecimal("850000"), "IN", "VND",
                "SMARTRENT-" + invoiceId + "-" + customerId, "ACB");

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoiceId, customerId, Payment.PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        bankHistoryClient.response = Optional.of(new BankHistoryResponseDto(200, "success", List.of(transaction)));

        PaymentCheckResponseDto response = paymentService.checkInvoicePayment(invoiceId, customerId);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void checkInvoicePaymentRejectsOtherCustomerInvoice() {
        UUID invoiceId = UUID.randomUUID();
        UUID actualCustomerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        Invoice invoice = invoice(invoiceId, actualCustomerId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("850000"));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> paymentService.checkInvoicePayment(invoiceId, otherCustomerId))
                .hasMessageContaining("quyền");
    }

    private Invoice invoice(UUID invoiceId, UUID customerId, UUID ownerId, UUID roomId, BigDecimal totalAmount) {
        Invoice invoice = Invoice.builder()
                .code(invoiceCode(invoiceId))
                .contractId(UUID.randomUUID())
                .ownerId(ownerId)
                .tenantUserId(customerId)
                .roomId(roomId)
                .periodYear((short) 2026)
                .periodMonth((short) 5)
                .issueDate(LocalDate.of(2026, 5, 1))
                .dueDate(LocalDate.of(2026, 5, 10))
                .rentAmount(totalAmount)
                .serviceAmount(BigDecimal.ZERO)
                .electricAmount(BigDecimal.ZERO)
                .waterAmount(BigDecimal.ZERO)
                .otherAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .lateFeeAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .paidAmount(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.PENDING)
                .build();
        invoice.setId(invoiceId);
        return invoice;
    }

    private Payment payment(UUID invoiceId, UUID customerId, BigDecimal amount, String paymentCode) {
        Payment payment = Payment.builder()
                .code("PAY-TEST")
                .invoiceId(invoiceId)
                .userId(customerId)
                .amount(amount)
                .method(Payment.PaymentMethod.BANK_TRANSFER)
                .paymentCode(paymentCode)
                .status(Payment.PaymentStatus.PENDING)
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }

    private Room room(UUID roomId, UUID ownerId, UUID customerId) {
        Room room = new Room();
        room.setId(roomId);
        room.setOwnerId(ownerId);
        room.setCurrentTenantId(customerId);
        room.setCurrentTenantPaidUntil(LocalDate.of(2026, 5, 31));
        return room;
    }

    private String invoiceCode(UUID invoiceId) {
        if (invoiceId != null && invoiceId.getMostSignificantBits() == 0L && invoiceId.getLeastSignificantBits() > 0L) {
            return "PT-HD-" + invoiceId.getLeastSignificantBits();
        }
        return "PT-HD-1";
    }

    private BankTransactionDto transaction(String transactionNumber,
                                           BigDecimal amount,
                                           String type,
                                           String currency,
                                           String description,
                                           String bankName) {
        return new BankTransactionDto(
                amount,
                null,
                "BUI THE KHIEM",
                transactionNumber,
                description,
                bankName,
                type,
                currency,
                1780851600000L,
                1780752311000L
        );
    }

    private static class StubBankHistoryClient extends BankHistoryClient {
        private Optional<BankHistoryResponseDto> response = Optional.empty();

        private StubBankHistoryClient(PaymentBankProperties bankProperties) {
            super(RestClient.builder().build(), bankProperties);
        }

        @Override
        public Optional<BankHistoryResponseDto> fetchHistory() {
            return response;
        }

        private static PaymentBankProperties bankProperties() {
            PaymentBankProperties properties = new PaymentBankProperties();
            properties.setApiUrl("https://bank.example/history");
            properties.setCode("ACB");
            properties.setAccountNumber("39118057");
            properties.setAccountName("BUI THE KHIEM");
            properties.setDisplayName("Ngân hàng ACB");
            return properties;
        }
    }
}
