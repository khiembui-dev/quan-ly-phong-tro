package vn.glassliving.admin.page.invoices;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoicesActionController {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final ContractRepository contractRepository;
    private final MessageSource messageSource;

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         Locale locale,
                         Model model) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound(msg(locale, "invoices.invoice")));
        if (!invoice.getOwnerId().equals(me.getId())) {
            throw BusinessException.forbidden(msg(locale, "invoices.detail.validation.forbidden"));
        }

        var tenant = userRepository.findById(invoice.getTenantUserId()).orElse(null);
        var room = roomRepository.findById(invoice.getRoomId()).orElse(null);
        var property = room != null ? propertyRepository.findById(room.getPropertyId()).orElse(null) : null;
        var contract = contractRepository.findById(invoice.getContractId()).orElse(null);
        List<InvoiceLineItem> lineItems = invoiceService.parseOtherItems(invoice);

        BigDecimal utilityAmount = nz(invoice.getElectricAmount()).add(nz(invoice.getWaterAmount()));
        BigDecimal remainingAmount = nz(invoice.getTotalAmount()).subtract(nz(invoice.getPaidAmount()));
        if (remainingAmount.signum() < 0) remainingAmount = BigDecimal.ZERO;

        model.addAttribute("activeNav", "invoices");
        model.addAttribute("pageTitle", msg(locale, "invoices.detailPageTitle", invoice.getCode()));
        model.addAttribute("invoice", invoice);
        model.addAttribute("tenant", tenant);
        model.addAttribute("room", room);
        model.addAttribute("property", property);
        model.addAttribute("contract", contract);
        model.addAttribute("lineItems", lineItems);
        model.addAttribute("utilityAmount", utilityAmount);
        model.addAttribute("remainingAmount", remainingAmount);
        return "admin/invoice-detail";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @RequestParam UUID contractId,
                         @RequestParam short year,
                         @RequestParam short month,
                         @RequestParam(required = false) BigDecimal electricPrev,
                         @RequestParam(required = false) BigDecimal electricCurr,
                         @RequestParam(required = false) BigDecimal waterPrev,
                         @RequestParam(required = false) BigDecimal waterCurr,
                         @RequestParam(required = false) BigDecimal otherAmount,
                         @RequestParam(required = false) BigDecimal discountAmount,
                         Locale locale,
                         RedirectAttributes ra) {
        try {
            Invoice inv = invoiceService.create(me.getId(), contractId, year, month,
                    electricPrev, electricCurr, waterPrev, waterCurr,
                    otherAmount, discountAmount);
            FlashAlert.ok(ra, msg(locale, "invoices.flash.created", inv.getCode(), month, year));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    @PostMapping("/batch")
    public String batchGenerate(@AuthenticationPrincipal AppUserDetails me,
                                @RequestParam short year,
                                @RequestParam short month,
                                Locale locale,
                                RedirectAttributes ra) {
        try {
            int n = invoiceService.batchGenerate(me.getId(), year, month);
            if (n == 0) {
                FlashAlert.info(ra, msg(locale, "invoices.flash.batchNone", month, year));
            } else {
                FlashAlert.ok(ra, msg(locale, "invoices.flash.batchCreated", n, month, year));
            }
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    @PostMapping("/{id}/mark-paid")
    public String markPaid(@AuthenticationPrincipal AppUserDetails me,
                           @PathVariable UUID id,
                           @RequestParam(required = false) String returnTo,
                           Locale locale,
                           RedirectAttributes ra) {
        try {
            Invoice inv = invoiceService.markPaid(me.getId(), id);
            FlashAlert.ok(ra, msg(locale, "invoices.flash.markPaid", inv.getCode()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectAfterAction(id, returnTo);
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@AuthenticationPrincipal AppUserDetails me,
                               @PathVariable UUID id,
                               @RequestParam String status,
                               @RequestParam(required = false) String returnTo,
                               Locale locale,
                               RedirectAttributes ra) {
        try {
            Invoice inv = invoiceService.updatePaymentStatus(me.getId(), id, status);
            FlashAlert.ok(ra, msg(locale, "invoices.flash.statusUpdated", inv.getCode(), statusLabel(locale, inv.getStatus())));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectAfterAction(id, returnTo);
    }

    @PostMapping("/{id}/remind")
    public String sendReminder(@AuthenticationPrincipal AppUserDetails me,
                               @PathVariable UUID id,
                               @RequestParam(required = false) String returnTo,
                               Locale locale,
                               RedirectAttributes ra) {
        try {
            Invoice inv = invoiceService.sendReminder(me.getId(), id);
            FlashAlert.ok(ra, msg(locale, "invoices.flash.reminderSent", inv.getCode()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return redirectAfterAction(id, returnTo);
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         @RequestParam(required = false) String returnTo,
                         Locale locale,
                         RedirectAttributes ra) {
        try {
            invoiceService.delete(me.getId(), id);
            FlashAlert.ok(ra, msg(locale, "invoices.flash.deleted"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/invoices";
    }

    private static String redirectAfterAction(UUID id, String returnTo) {
        if ("detail".equals(returnTo)) {
            return "redirect:/admin/invoices/" + id;
        }
        return "redirect:/admin/invoices";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String statusLabel(Locale locale, Invoice.InvoiceStatus status) {
        return switch (status) {
            case PAID -> msg(locale, "invoices.paid").toLowerCase(locale);
            case PENDING -> msg(locale, "invoices.unpaid").toLowerCase(locale);
            case PARTIALLY_PAID -> msg(locale, "invoices.partiallyPaid").toLowerCase(locale);
            case OVERDUE -> msg(locale, "invoices.overdue").toLowerCase(locale);
            case CANCELLED -> msg(locale, "invoices.cancelled").toLowerCase(locale);
        };
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
