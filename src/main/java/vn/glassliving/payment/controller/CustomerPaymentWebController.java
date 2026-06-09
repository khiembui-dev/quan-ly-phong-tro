package vn.glassliving.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.dto.PaymentCheckResponseDto;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.payment.service.PaymentService;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CustomerPaymentWebController {
    private static final long CHECKOUT_EXPIRY_MINUTES = 30;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final ContractRepository contractRepository;
    private final PaymentBankProperties bankProperties;

    @GetMapping({"/me/invoices/{id}", "/customer/invoice-detail/{id}"})
    public String invoiceDetail(@AuthenticationPrincipal AppUserDetails me,
                                @PathVariable UUID id,
                                Model model) {
        Invoice invoice = loadTenantInvoice(me.getId(), id);
        var room = invoice.getRoomId() != null ? roomRepository.findById(invoice.getRoomId()).orElse(null) : null;
        var property = room != null && room.getPropertyId() != null
                ? propertyRepository.findById(room.getPropertyId()).orElse(null)
                : null;

        model.addAttribute("invoice", invoice);
        model.addAttribute("lineItems", invoiceService.parseOtherItems(invoice));
        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("contract", contractRepository.findById(invoice.getContractId()).orElse(null));
        model.addAttribute("payments", paymentRepository.findTop10ByInvoiceIdOrderByCreatedAtDesc(invoice.getId()));
        model.addAttribute("utilityAmount", nz(invoice.getElectricAmount()).add(nz(invoice.getWaterAmount())));
        model.addAttribute("remainingAmount", paymentService.remainingAmount(invoice));
        model.addAttribute("payable", invoice.getStatus() != Invoice.InvoiceStatus.PAID
                && invoice.getStatus() != Invoice.InvoiceStatus.CANCELLED
                && paymentService.remainingAmount(invoice).signum() > 0);
        return "customer/invoice-detail";
    }

    @PostMapping({"/me/invoices/{id}/pay", "/customer/invoices/{id}/pay"})
    public String startInvoicePayment(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID id,
                                      @RequestParam(defaultValue = "BANK_TRANSFER") String method,
                                      RedirectAttributes ra) {
        try {
            paymentService.createInvoicePayment(me.getId(), id, method);
            return "redirect:/customer/invoices/" + id + "/pay";
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
            return "redirect:/me/invoices/" + id;
        }
    }

    @GetMapping({"/customer/invoices/{invoiceId}/pay", "/me/invoices/{invoiceId}/pay"})
    public String invoiceCheckout(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID invoiceId,
                                  Model model) {
        Payment payment = paymentService.getOrCreatePaymentForInvoice(invoiceId, me.getId());
        Invoice invoice = loadTenantInvoice(me.getId(), invoiceId);
        addCheckoutModel(model, invoice, payment);
        return "customer/payment-checkout";
    }

    @GetMapping({"/me/payments/{id}", "/customer/payment-checkout/{id}"})
    public String paymentCheckout(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID id,
                                  Model model) {
        Payment payment = paymentService.getOwned(me.getId(), id);
        Invoice invoice = payment.getInvoiceId() != null
                ? loadTenantInvoice(me.getId(), payment.getInvoiceId())
                : null;
        addCheckoutModel(model, invoice, payment);
        return "customer/payment-checkout";
    }

    @PostMapping("/me/payments/{id}/complete")
    public String completePayment(@AuthenticationPrincipal AppUserDetails me,
                                  @PathVariable UUID id,
                                  RedirectAttributes ra) {
        try {
            Payment payment = paymentService.getOwned(me.getId(), id);
            FlashAlert.ok(ra, "Đã ghi nhận yêu cầu. Hãy bấm Kiểm tra thanh toán sau 1-3 phút.");
            return payment.getInvoiceId() != null
                    ? "redirect:/customer/invoices/" + payment.getInvoiceId() + "/pay"
                    : "redirect:/me/payments/" + payment.getId();
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
            return "redirect:/me/payments/" + id;
        }
    }

    @GetMapping("/customer/payments/{invoiceId}/check")
    @ResponseBody
    public PaymentCheckResponseDto checkPayment(@AuthenticationPrincipal AppUserDetails me,
                                                @PathVariable UUID invoiceId) {
        return paymentService.checkInvoicePayment(invoiceId, me.getId());
    }

    private void addCheckoutModel(Model model, Invoice invoice, Payment payment) {
        var room = invoice != null && invoice.getRoomId() != null
                ? roomRepository.findById(invoice.getRoomId()).orElse(null)
                : null;
        var property = room != null && room.getPropertyId() != null
                ? propertyRepository.findById(room.getPropertyId()).orElse(null)
                : null;

        model.addAttribute("payment", payment);
        model.addAttribute("invoice", invoice);
        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("paidUntil", room != null ? room.getCurrentTenantPaidUntil() : null);
        model.addAttribute("lineItems", invoice != null && invoiceService != null ? invoiceService.parseOtherItems(invoice) : java.util.List.of());
        model.addAttribute("utilityAmount", invoice != null ? nz(invoice.getElectricAmount()).add(nz(invoice.getWaterAmount())) : BigDecimal.ZERO);
        model.addAttribute("bank", bankProperties);
        model.addAttribute("transferContent", payment.getPaymentCode());
        model.addAttribute("qrUrl", buildQrUrl(invoice, payment));
        model.addAttribute("checkUrl", invoice != null ? "/customer/payments/" + invoice.getId() + "/check" : "");
        model.addAttribute("checkoutExpiresAt", OffsetDateTime.now().plusMinutes(CHECKOUT_EXPIRY_MINUTES));
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

    private String buildQrUrl(Invoice invoice, Payment payment) {
        if (bankProperties.getAccountNumber() == null || bankProperties.getAccountNumber().isBlank()
                || invoice == null || payment == null || payment.getPaymentCode() == null) {
            return null;
        }
        return "https://img.vietqr.io/image/" + encode(bankProperties.getCode()) + "-"
                + encode(bankProperties.getAccountNumber())
                + "-compact2.png?amount=" + payment.getAmount().toPlainString()
                + "&addInfo=" + encode(payment.getPaymentCode())
                + "&accountName=" + encode(bankProperties.getAccountName());
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
