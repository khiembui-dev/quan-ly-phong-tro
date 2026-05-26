package vn.glassliving.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.payment.service.PaymentService;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CustomerPaymentWebController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    @GetMapping("/me/invoices/{id}")
    public String invoiceDetail(@AuthenticationPrincipal AppUserDetails me,
                                @PathVariable UUID id,
                                Model model) {
        Invoice invoice = loadTenantInvoice(me.getId(), id);
        var room = roomRepository.findById(invoice.getRoomId()).orElse(null);
        var property = room != null && room.getPropertyId() != null
                ? propertyRepository.findById(room.getPropertyId()).orElse(null)
                : null;

        model.addAttribute("invoice", invoice);
        model.addAttribute("lineItems", invoiceService.parseOtherItems(invoice));
        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("payments", paymentRepository.findTop10ByInvoiceIdOrderByCreatedAtDesc(invoice.getId()));
        model.addAttribute("utilityAmount", nz(invoice.getElectricAmount()).add(nz(invoice.getWaterAmount())));
        model.addAttribute("remainingAmount", paymentService.remainingAmount(invoice));
        model.addAttribute("payable", invoice.getStatus() != Invoice.InvoiceStatus.PAID
                && invoice.getStatus() != Invoice.InvoiceStatus.CANCELLED
                && paymentService.remainingAmount(invoice).signum() > 0);
        return "customer/invoice-detail";
    }

    @PostMapping("/me/invoices/{id}/pay")
    public String startInvoicePayment(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID id,
                                      @RequestParam(defaultValue = "VNPAY") String method,
                                      RedirectAttributes ra) {
        try {
            Payment payment = paymentService.createInvoicePayment(me.getId(), id, method);
            return "redirect:/me/payments/" + payment.getId();
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
            return "redirect:/me/invoices/" + id;
        }
    }

    @GetMapping("/me/payments/{id}")
    public String paymentCheckout(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID id,
                                  Model model) {
        Payment payment = paymentService.getOwned(me.getId(), id);
        Invoice invoice = payment.getInvoiceId() != null
                ? invoiceRepository.findById(payment.getInvoiceId()).orElse(null)
                : null;
        if (invoice != null && !me.getId().equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền xem phiên thanh toán này.");
        }
        model.addAttribute("payment", payment);
        model.addAttribute("invoice", invoice);
        model.addAttribute("lineItems", invoice != null ? invoiceService.parseOtherItems(invoice) : java.util.List.of());
        model.addAttribute("utilityAmount", invoice != null ? nz(invoice.getElectricAmount()).add(nz(invoice.getWaterAmount())) : BigDecimal.ZERO);
        return "customer/payment-checkout";
    }

    @PostMapping("/me/payments/{id}/complete")
    public String completePayment(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID id,
                                  RedirectAttributes ra) {
        try {
            Payment payment = paymentService.completeInvoicePayment(me.getId(), id);
            FlashAlert.ok(ra, "Thanh toán thành công. Hóa đơn đã được cập nhật.");
            return payment.getInvoiceId() != null
                    ? "redirect:/me/invoices/" + payment.getInvoiceId()
                    : "redirect:/me/invoices";
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
            return "redirect:/me/payments/" + id;
        }
    }

    private Invoice loadTenantInvoice(UUID userId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if (!userId.equals(invoice.getTenantUserId())) {
            throw BusinessException.forbidden("Bạn không có quyền xem hóa đơn này.");
        }
        return invoice;
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
