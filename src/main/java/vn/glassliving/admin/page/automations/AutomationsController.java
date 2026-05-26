package vn.glassliving.admin.page.automations;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.automation.service.AutomationEmailService;
import vn.glassliving.automation.service.AutomationScheduler;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/automations")
@RequiredArgsConstructor
public class AutomationsController {

    private final AutomationSettingRepository repository;
    private final AutomationEmailService emailService;
    private final AutomationScheduler automationScheduler;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String settings(@AuthenticationPrincipal AppUserDetails me, Model model) {
        AutomationSetting setting = loadOrCreate(me);
        LocalDate today = LocalDate.now();
        int contractDays = clamp(setting.getContractRenewAlertDays(), 1, 180, 30);

        model.addAttribute("activeNav", "automations");
        model.addAttribute("pageTitle", "Tự động hóa");
        model.addAttribute("setting", setting);
        model.addAttribute("mailReady", emailService.mailUsable(setting));
        model.addAttribute("testEmailDefault", me.getEmail());
        model.addAttribute("expiringContractCount", contractRepository.countByOwnerIdAndStatusAndEndDateBetween(
                me.getId(), Contract.ContractStatus.ACTIVE, today, today.plusDays(contractDays)));
        model.addAttribute("paymentReminderCandidateCount", invoiceRepository.countByOwnerIdAndStatusInAndDueDateBetween(
                me.getId(), List.of(Invoice.InvoiceStatus.PENDING, Invoice.InvoiceStatus.PARTIALLY_PAID, Invoice.InvoiceStatus.OVERDUE),
                today, today.plusDays(14)));
        return "admin/automations";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails me,
                       @RequestParam(defaultValue = "false") boolean smtpEnabled,
                       @RequestParam(required = false) String smtpHost,
                       @RequestParam(defaultValue = "587") Integer smtpPort,
                       @RequestParam(required = false) String smtpUsername,
                       @RequestParam(required = false) String smtpPassword,
                       @RequestParam(defaultValue = "false") boolean clearSmtpPassword,
                       @RequestParam(required = false) String smtpFromEmail,
                       @RequestParam(required = false) String smtpFromName,
                       @RequestParam(required = false) String contactEmail,
                       @RequestParam(required = false) String contactZalo,
                       @RequestParam(defaultValue = "false") boolean smtpAuth,
                       @RequestParam(defaultValue = "false") boolean smtpStartTls,
                       @RequestParam(defaultValue = "false") boolean smtpSslTrust,
                       @RequestParam(defaultValue = "false") boolean invoiceEmailEnabled,
                       @RequestParam(defaultValue = "false") boolean paymentReminderEmailEnabled,
                       @RequestParam(defaultValue = "false") boolean contractExpiryEmailEnabled,
                       @RequestParam(defaultValue = "false") boolean invoiceAutoCreate,
                       @RequestParam(defaultValue = "1") Short invoiceCreateDay,
                       @RequestParam(required = false) String reminderPreDueDays,
                       @RequestParam(required = false) String reminderOverdueDays,
                       @RequestParam(defaultValue = "false") boolean reminderChannelEmail,
                       @RequestParam(defaultValue = "false") boolean reminderChannelSms,
                       @RequestParam(defaultValue = "false") boolean reminderChannelZalo,
                       @RequestParam(defaultValue = "30") Short contractRenewAlertDays,
                       @RequestParam(defaultValue = "false") boolean autoLateFeeEnabled,
                       @RequestParam(defaultValue = "0") BigDecimal autoLateFeePct,
                       @RequestParam(defaultValue = "5") Short autoLateFeeAfterDays,
                       @RequestParam(defaultValue = "21") Short quietHoursStart,
                       @RequestParam(defaultValue = "8") Short quietHoursEnd,
                       RedirectAttributes ra) {
        try {
            AutomationSetting setting = loadOrCreate(me);
            setting.setSmtpEnabled(smtpEnabled);
            setting.setSmtpHost(clean(smtpHost));
            setting.setSmtpPort(clamp(smtpPort, 1, 65535, 587));
            setting.setSmtpUsername(clean(smtpUsername));
            if (clearSmtpPassword) {
                setting.setSmtpPassword(null);
            } else if (clean(smtpPassword) != null) {
                setting.setSmtpPassword(smtpPassword.trim());
            }
            setting.setSmtpFromEmail(clean(smtpFromEmail));
            setting.setSmtpFromName(clean(smtpFromName));
            setting.setContactEmail(clean(contactEmail));
            setting.setContactZalo(clean(contactZalo));
            setting.setSmtpAuth(smtpAuth);
            setting.setSmtpStartTls(smtpStartTls);
            setting.setSmtpSslTrust(smtpSslTrust);

            setting.setInvoiceEmailEnabled(invoiceEmailEnabled);
            setting.setPaymentReminderEmailEnabled(paymentReminderEmailEnabled);
            setting.setContractExpiryEmailEnabled(contractExpiryEmailEnabled);
            setting.setInvoiceAutoCreate(invoiceAutoCreate);
            setting.setInvoiceCreateDay((short) clamp(invoiceCreateDay, 1, 28, 1));
            setting.setReminderPreDueDays(clean(reminderPreDueDays) != null ? reminderPreDueDays.trim() : "7,3,1");
            setting.setReminderOverdueDays(clean(reminderOverdueDays) != null ? reminderOverdueDays.trim() : "1,3,7,14");
            setting.setReminderChannelEmail(reminderChannelEmail);
            setting.setReminderChannelSms(reminderChannelSms);
            setting.setReminderChannelZalo(reminderChannelZalo);
            setting.setContractRenewAlertDays((short) clamp(contractRenewAlertDays, 1, 180, 30));
            setting.setAutoLateFeeEnabled(autoLateFeeEnabled);
            setting.setAutoLateFeePct(autoLateFeePct != null ? autoLateFeePct : BigDecimal.ZERO);
            setting.setAutoLateFeeAfterDays((short) clamp(autoLateFeeAfterDays, 0, 60, 5));
            setting.setQuietHoursStart((short) clamp(quietHoursStart, 0, 23, 21));
            setting.setQuietHoursEnd((short) clamp(quietHoursEnd, 0, 23, 8));

            validateSmtp(setting);
            repository.save(setting);
            FlashAlert.ok(ra, "Đã lưu cấu hình tự động gửi email.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/automations";
    }

    @PostMapping("/test-email")
    public String testEmail(@AuthenticationPrincipal AppUserDetails me,
                            @RequestParam(required = false) String testEmail,
                            RedirectAttributes ra) {
        try {
            emailService.sendTest(me.getId(), clean(testEmail) != null ? testEmail : me.getEmail());
            FlashAlert.ok(ra, "Đã gửi email kiểm tra. Vui lòng xem hộp thư nhận.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/automations";
    }

    @PostMapping("/run-contract-reminders")
    public String runContractReminders(@AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        int sent = automationScheduler.runContractExpiryNow(me.getId());
        FlashAlert.ok(ra, "Đã gửi " + sent + " email nhắc hợp đồng sắp hết hạn.");
        return "redirect:/admin/automations";
    }

    private AutomationSetting loadOrCreate(AppUserDetails me) {
        return repository.findByOwnerId(me.getId())
                .orElseGet(() -> repository.save(AutomationSetting.builder().ownerId(me.getId()).build()));
    }

    private static void validateSmtp(AutomationSetting setting) {
        if (!setting.isSmtpEnabled()) return;
        if (clean(setting.getSmtpHost()) == null) {
            throw BusinessException.badRequest("Vui lòng nhập SMTP host.");
        }
        if (clean(setting.getSmtpFromEmail()) == null) {
            throw BusinessException.badRequest("Vui lòng nhập email người gửi.");
        }
        if (setting.isSmtpAuth() && clean(setting.getSmtpUsername()) == null) {
            throw BusinessException.badRequest("SMTP bật xác thực cần nhập username.");
        }
        if (setting.isSmtpAuth() && clean(setting.getSmtpPassword()) == null) {
            throw BusinessException.badRequest("SMTP bật xác thực cần nhập mật khẩu hoặc app password.");
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isBlank() ? null : s;
    }

    private static int clamp(Number value, int min, int max, int fallback) {
        int n = value != null ? value.intValue() : fallback;
        return Math.max(min, Math.min(max, n));
    }
}
