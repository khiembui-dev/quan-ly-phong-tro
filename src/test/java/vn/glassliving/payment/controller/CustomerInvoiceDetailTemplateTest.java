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
import vn.glassliving.payment.entity.Payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerInvoiceDetailTemplateTest {

    @Test
    void invoiceDetailDoesNotRenderPaymentHistorySection() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder()
                .code("PT-HD-8")
                .tenantUserId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .periodMonth((short) 8)
                .periodYear((short) 2026)
                .issueDate(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 5))
                .rentAmount(new BigDecimal("10000"))
                .electricAmount(new BigDecimal("1000"))
                .waterAmount(new BigDecimal("1000"))
                .serviceAmount(BigDecimal.ZERO)
                .otherAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .lateFeeAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("12000"))
                .paidAmount(new BigDecimal("12000"))
                .status(Invoice.InvoiceStatus.PAID)
                .build();
        invoice.setId(invoiceId);

        Payment payment = Payment.builder()
                .code("PAY-TEST")
                .invoiceId(invoiceId)
                .amount(new BigDecimal("12000"))
                .status(Payment.PaymentStatus.PAID)
                .build();
        payment.setId(UUID.randomUUID());

        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("invoice", invoice);
        context.setVariable("room", null);
        context.setVariable("property", null);
        context.setVariable("contract", null);
        context.setVariable("lineItems", List.of());
        context.setVariable("remainingAmount", BigDecimal.ZERO);
        context.setVariable("payable", false);
        context.setVariable("payments", List.of(payment));
        context.setVariable("h", new TemplateMoneyHelper());

        String html = templateEngine().process("customer/invoice-detail", context);

        assertThat(html).doesNotContain("Lịch sử thanh toán");
        assertThat(html).doesNotContain("PAY-TEST");
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
