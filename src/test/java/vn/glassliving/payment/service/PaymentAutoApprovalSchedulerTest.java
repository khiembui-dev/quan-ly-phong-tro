package vn.glassliving.payment.service;

import org.junit.jupiter.api.Test;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.payment.dto.PaymentCheckResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAutoApprovalSchedulerTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final StubPaymentService paymentService = new StubPaymentService();
    private final PaymentAutoApprovalScheduler scheduler = new PaymentAutoApprovalScheduler(invoiceRepository, paymentService);

    @Test
    void runOnceChecksOnlyOpenTenantInvoices() {
        UUID tenantId = UUID.randomUUID();
        Invoice pending = invoice(tenantId, Invoice.InvoiceStatus.PENDING);
        Invoice overdue = invoice(tenantId, Invoice.InvoiceStatus.OVERDUE);
        Invoice noTenant = invoice(null, Invoice.InvoiceStatus.PENDING);

        when(invoiceRepository.findByStatusIn(any(Collection.class))).thenReturn(List.of(pending, overdue, noTenant));
        paymentService.respond(pending.getId(), PaymentCheckResponseDto.paid(new BigDecimal("12000"), OffsetDateTime.now()));
        paymentService.respond(overdue.getId(), PaymentCheckResponseDto.pending());

        PaymentAutoApprovalScheduler.Result result = scheduler.runOnce();

        assertThat(result.checked()).isEqualTo(2);
        assertThat(result.approved()).isEqualTo(1);
        assertThat(paymentService.calls()).containsExactly(
                new StubPaymentService.Call(pending.getId(), tenantId),
                new StubPaymentService.Call(overdue.getId(), tenantId)
        );
        assertThat(paymentService.calls()).noneMatch(call -> call.invoiceId().equals(noTenant.getId()));
    }

    @Test
    void repositoryQueryDoesNotIncludePaidOrCancelledInvoices() {
        when(invoiceRepository.findByStatusIn(any(Collection.class))).thenReturn(List.of());

        scheduler.runOnce();

        verify(invoiceRepository).findByStatusIn(eq(List.of(
                Invoice.InvoiceStatus.PENDING,
                Invoice.InvoiceStatus.PARTIALLY_PAID,
                Invoice.InvoiceStatus.OVERDUE
        )));
    }

    private static Invoice invoice(UUID tenantId, Invoice.InvoiceStatus status) {
        Invoice invoice = Invoice.builder()
                .code("PT-HD-" + UUID.randomUUID().toString().substring(0, 4))
                .ownerId(UUID.randomUUID())
                .tenantUserId(tenantId)
                .contractId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .periodYear((short) 2026)
                .periodMonth((short) 8)
                .issueDate(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 5))
                .rentAmount(new BigDecimal("10000"))
                .totalAmount(new BigDecimal("12000"))
                .paidAmount(BigDecimal.ZERO)
                .status(status)
                .build();
        invoice.setId(UUID.randomUUID());
        return invoice;
    }

    private static final class StubPaymentService extends PaymentService {
        private final Map<UUID, PaymentCheckResponseDto> responses = new HashMap<>();
        private final List<Call> calls = new ArrayList<>();

        private StubPaymentService() {
            super(null, null, null, null, null, null);
        }

        void respond(UUID invoiceId, PaymentCheckResponseDto response) {
            responses.put(invoiceId, response);
        }

        List<Call> calls() {
            return calls;
        }

        @Override
        public PaymentCheckResponseDto checkInvoicePayment(UUID invoiceId, UUID currentCustomer) {
            calls.add(new Call(invoiceId, currentCustomer));
            return responses.getOrDefault(invoiceId, PaymentCheckResponseDto.pending());
        }

        private record Call(UUID invoiceId, UUID customerId) {}
    }
}
