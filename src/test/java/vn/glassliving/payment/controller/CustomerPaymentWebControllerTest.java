package vn.glassliving.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.payment.service.BankHistoryClient;
import vn.glassliving.payment.service.PaymentService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerPaymentWebControllerTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final vn.glassliving.invoice.service.InvoiceService invoiceService = null;
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final PaymentBankProperties bankProperties = bankProperties();
    private final PaymentService paymentService = new PaymentService(
            paymentRepository,
            invoiceRepository,
            null,
            roomRepository,
            new StubBankHistoryClient(bankProperties),
            bankProperties
    );
    private final CustomerPaymentWebController controller = new CustomerPaymentWebController(
            invoiceRepository,
            invoiceService,
            paymentRepository,
            paymentService,
            roomRepository,
            propertyRepository,
            contractRepository,
            bankProperties
    );

    @Test
    void invoiceCheckoutLoadsOwnedInvoiceAndBuildsVietQr() {
        UUID invoiceId = new UUID(0L, 1L);
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        String paymentCode = "PT-1";
        Invoice invoice = invoice(invoiceId, customerId, new BigDecimal("850000"));
        invoice.setOwnerId(ownerId);
        invoice.setRoomId(roomId);
        Payment payment = Payment.builder()
                .code("PAY-TEST")
                .invoiceId(invoiceId)
                .userId(customerId)
                .amount(new BigDecimal("850000"))
                .method(Payment.PaymentMethod.BANK_TRANSFER)
                .paymentCode(paymentCode)
                .status(Payment.PaymentStatus.PENDING)
                .build();
        payment.setId(UUID.randomUUID());
        invoice.setPaymentCode(paymentCode);
        Room room = room(roomId, ownerId, propertyId, customerId);
        Property property = property(propertyId, ownerId);
        AppUserDetails me = customer(customerId);
        ConcurrentModel model = new ConcurrentModel();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findFirstByInvoiceIdAndUserIdAndStatusOrderByCreatedAtDesc(
                invoiceId, customerId, Payment.PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        String view = controller.invoiceCheckout(me, invoiceId, model);

        assertThat(view).isEqualTo("customer/payment-checkout");
        assertThat(model.getAttribute("payment")).isSameAs(payment);
        assertThat(model.getAttribute("invoice")).isSameAs(invoice);
        assertThat(model.getAttribute("room")).isSameAs(room);
        assertThat(model.getAttribute("property")).isSameAs(property);
        assertThat(model.getAttribute("paidUntil")).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(model.getAttribute("transferContent")).isEqualTo(paymentCode);
        assertThat(model.getAttribute("qrUrl").toString())
                .contains("https://img.vietqr.io/image/ACB-39118057-compact2.png")
                .contains("amount=850000")
                .contains("addInfo=PT-1")
                .contains("accountName=BUI%20THE%20KHIEM");
    }

    @Test
    void invoiceCheckoutDisplaysMissingRoomWithoutFailing() {
        UUID invoiceId = new UUID(0L, 2L);
        UUID customerId = UUID.randomUUID();
        Invoice invoice = invoice(invoiceId, customerId, new BigDecimal("850000"));
        invoice.setRoomId(null);
        AppUserDetails me = customer(customerId);
        ConcurrentModel model = new ConcurrentModel();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String view = controller.invoiceCheckout(me, invoiceId, model);

        assertThat(view).isEqualTo("customer/payment-checkout");
        assertThat(model.getAttribute("room")).isNull();
        assertThat(model.getAttribute("property")).isNull();
        assertThat(model.getAttribute("paidUntil")).isNull();
        assertThat(model.getAttribute("transferContent")).isEqualTo("PT-2");
    }

    private AppUserDetails customer(UUID customerId) {
        User user = User.builder()
                .email("tenant@example.com")
                .fullName("Tenant")
                .passwordHash("hash")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(User.Role.TENANT))
                .build();
        user.setId(customerId);
        return new AppUserDetails(user);
    }

    private Invoice invoice(UUID invoiceId, UUID customerId, BigDecimal totalAmount) {
        Invoice invoice = Invoice.builder()
                .code(invoiceCode(invoiceId))
                .contractId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .tenantUserId(customerId)
                .roomId(UUID.randomUUID())
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

    private Room room(UUID roomId, UUID ownerId, UUID propertyId, UUID customerId) {
        Room room = new Room();
        room.setId(roomId);
        room.setOwnerId(ownerId);
        room.setPropertyId(propertyId);
        room.setCode("P101");
        room.setTitle("Phòng 101");
        room.setCurrentTenantId(customerId);
        room.setCurrentTenantPaidUntil(LocalDate.of(2026, 5, 31));
        return room;
    }

    private Property property(UUID propertyId, UUID ownerId) {
        Property property = new Property();
        property.setId(propertyId);
        property.setOwnerId(ownerId);
        property.setName("Cơ sở Quận 1");
        return property;
    }

    private String invoiceCode(UUID invoiceId) {
        if (invoiceId != null && invoiceId.getMostSignificantBits() == 0L && invoiceId.getLeastSignificantBits() > 0L) {
            return "PT-HD-" + invoiceId.getLeastSignificantBits();
        }
        return "PT-HD-1";
    }

    private PaymentBankProperties bankProperties() {
        PaymentBankProperties properties = new PaymentBankProperties();
        properties.setApiUrl("https://secret.example/history");
        properties.setCode("ACB");
        properties.setAccountNumber("39118057");
        properties.setAccountName("BUI THE KHIEM");
        properties.setDisplayName("Ngân hàng ACB");
        return properties;
    }

    private static class StubBankHistoryClient extends BankHistoryClient {
        private StubBankHistoryClient(PaymentBankProperties properties) {
            super(RestClient.builder().build(), properties);
        }
    }
}
