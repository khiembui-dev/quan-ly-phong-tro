package vn.glassliving.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.payment.dto.PaymentCheckResponseDto;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAutoApprovalScheduler {

    private static final List<Invoice.InvoiceStatus> OPEN_STATUSES = List.of(
            Invoice.InvoiceStatus.PENDING,
            Invoice.InvoiceStatus.PARTIALLY_PAID,
            Invoice.InvoiceStatus.OVERDUE
    );

    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;

    @Scheduled(
            fixedDelayString = "${app.payment.auto-check.fixed-delay-ms:15000}",
            initialDelayString = "${app.payment.auto-check.initial-delay-ms:10000}"
    )
    public void autoCheckOpenInvoices() {
        runOnce();
    }

    public Result runOnce() {
        int checked = 0;
        int approved = 0;
        for (Invoice invoice : invoiceRepository.findByStatusIn(OPEN_STATUSES)) {
            UUID tenantUserId = invoice.getTenantUserId();
            if (tenantUserId == null) {
                continue;
            }
            try {
                PaymentCheckResponseDto response = paymentService.checkInvoicePayment(invoice.getId(), tenantUserId);
                checked++;
                if ("PAID".equals(response.status())) {
                    approved++;
                }
            } catch (BusinessException | IllegalArgumentException ex) {
                log.debug("Skipped auto payment check for invoice {}", invoice.getId());
            } catch (RuntimeException ex) {
                log.warn("Auto payment check failed for invoice {}", invoice.getId());
            }
        }
        if (approved > 0) {
            log.info("Auto-approved {} invoice payment(s)", approved);
        }
        return new Result(checked, approved);
    }

    public record Result(int checked, int approved) {}
}
