package vn.glassliving.admin.page.invoices;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;

import java.time.YearMonth;
import java.util.UUID;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoicesPageController {

    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String invoices(@AuthenticationPrincipal AppUserDetails me,
                           @RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           Model model) {
        UUID ownerId = me.getId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "issueDate"));

        Page<Invoice> invoicePage = (status == null || status.isBlank())
                ? invoiceRepository.findByOwnerId(ownerId, pageable)
                : invoiceRepository.findByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.valueOf(status), pageable);

        model.addAttribute("activeNav", "invoices");
        model.addAttribute("pageTitle", "Hóa đơn");
        model.addAttribute("invoicePage", invoicePage);
        model.addAttribute("invoices", invoicePage.getContent());
        model.addAttribute("filter", status);
        model.addAttribute("paid", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PAID));
        model.addAttribute("pending", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING));
        model.addAttribute("overdue", invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE));
        model.addAttribute("paidCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PAID));
        model.addAttribute("pendingCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PENDING));
        model.addAttribute("overdueCount", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE));

        YearMonth currentPeriod = YearMonth.now();
        model.addAttribute("currentYear", (short) currentPeriod.getYear());
        model.addAttribute("currentMonth", (short) currentPeriod.getMonthValue());
        return "admin/invoices";
    }
}
