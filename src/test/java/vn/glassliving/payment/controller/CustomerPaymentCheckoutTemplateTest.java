package vn.glassliving.payment.controller;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.ILinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.property.entity.Property;
import vn.glassliving.room.entity.Room;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerPaymentCheckoutTemplateTest {

    @Test
    void paymentCheckoutTemplateRendersPendingBankTransferState() {
        UUID invoiceId = new UUID(0L, 1L);
        UUID customerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        Invoice invoice = Invoice.builder()
                .code("PT-HD-1")
                .contractId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .tenantUserId(customerId)
                .roomId(roomId)
                .periodYear((short) 2026)
                .periodMonth((short) 5)
                .issueDate(LocalDate.of(2026, 5, 1))
                .dueDate(LocalDate.of(2026, 5, 10))
                .rentAmount(new BigDecimal("850000"))
                .serviceAmount(BigDecimal.ZERO)
                .electricAmount(BigDecimal.ZERO)
                .waterAmount(BigDecimal.ZERO)
                .otherAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .lateFeeAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("850000"))
                .paidAmount(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.PENDING)
                .build();
        invoice.setId(invoiceId);

        Payment payment = Payment.builder()
                .code("PAY-TEST")
                .invoiceId(invoiceId)
                .userId(customerId)
                .amount(new BigDecimal("850000"))
                .method(Payment.PaymentMethod.BANK_TRANSFER)
                .paymentCode("PT-1")
                .status(Payment.PaymentStatus.PENDING)
                .build();
        payment.setId(UUID.randomUUID());

        Room room = new Room();
        room.setId(roomId);
        room.setPropertyId(propertyId);
        room.setCode("P101");
        room.setTitle("Phong 101");
        room.setCurrentTenantPaidUntil(LocalDate.of(2026, 5, 31));

        Property property = new Property();
        property.setId(propertyId);
        property.setName("Co so Quan 1");

        PaymentBankProperties bank = new PaymentBankProperties();
        bank.setCode("ACB");
        bank.setAccountNumber("39118057");
        bank.setAccountName("BUI THE KHIEM");
        bank.setDisplayName("Ngan hang ACB");

        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("invoice", invoice);
        context.setVariable("payment", payment);
        context.setVariable("room", room);
        context.setVariable("property", property);
        context.setVariable("paidUntil", room.getCurrentTenantPaidUntil());
        context.setVariable("lineItems", List.of());
        context.setVariable("utilityAmount", BigDecimal.ZERO);
        context.setVariable("bank", bank);
        context.setVariable("transferContent", "PT-1");
        context.setVariable("qrUrl", "https://img.vietqr.io/image/ACB-39118057-compact2.png?amount=850000&addInfo=PT-1");
        context.setVariable("checkUrl", "/customer/payments/" + invoiceId + "/check");
        context.setVariable("checkoutExpiresAt", OffsetDateTime.of(2026, 6, 8, 12, 30, 0, 0, ZoneOffset.ofHours(7)));
        context.setVariable("h", new TemplateMoneyHelper());

        String html = templateEngine().process("customer/payment-checkout", context);

        assertThat(html).contains("PT-1");
        assertThat(html).contains("PENDING");
        assertThat(html).contains("P101");
        assertThat(html).contains("39118057");
        assertThat(html).contains("31/05/2026");
        assertThat(html).contains("data-payment-expires-at=\"2026-06-08T12:30");
        assertThat(html).contains("data-payment-countdown");
    }

    private SpringTemplateEngine templateEngine() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new RootLinkBuilder());
        return engine;
    }

    public static class TemplateMoneyHelper {
        public String vnd(BigDecimal amount) {
            return MoneyFormatter.vnd(amount);
        }
    }

    private static class RootLinkBuilder implements ILinkBuilder {
        @Override
        public String getName() {
            return "test-root-link-builder";
        }

        @Override
        public Integer getOrder() {
            return 1;
        }

        @Override
        public String buildLink(IExpressionContext context, String base, Map<String, Object> parameters) {
            return base;
        }
    }
}
