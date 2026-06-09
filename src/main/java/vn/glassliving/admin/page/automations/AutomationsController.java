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
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.context.MessageSource;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.automation.service.AutomationEmailService;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;

import java.math.BigDecimal;
import java.util.Locale;

@Controller
@RequestMapping({"/admin/automations", "/admin/system-settings"})
@RequiredArgsConstructor
public class AutomationsController {

    private final AutomationSettingRepository repository;
    private final AutomationEmailService emailService;
    private final MessageSource messageSource;

    @GetMapping
    public String settings(@AuthenticationPrincipal AppUserDetails me, Locale locale, Model model) {
        AutomationSetting setting = loadOrCreate(me);

        model.addAttribute("activeNav", "automations");
        model.addAttribute("pageTitle", msg(locale, "system.title"));
        model.addAttribute("setting", setting);
        model.addAttribute("mailReady", emailService.mailUsable(setting));
        model.addAttribute("testEmailDefault", me.getEmail());
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
                       @RequestParam(defaultValue = "30") Short contractRenewAlertDays,
                       @RequestParam(defaultValue = "false") boolean autoLateFeeEnabled,
                       @RequestParam(defaultValue = "0") BigDecimal autoLateFeePct,
                       @RequestParam(defaultValue = "5") Short autoLateFeeAfterDays,
                       @RequestParam(defaultValue = "21") Short quietHoursStart,
                       @RequestParam(defaultValue = "8") Short quietHoursEnd,
                       Locale locale,
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
            setting.setReminderChannelEmail(true);
            setting.setReminderChannelSms(false);
            setting.setReminderChannelZalo(false);
            setting.setContractRenewAlertDays((short) clamp(contractRenewAlertDays, 1, 180, 30));
            setting.setAutoLateFeeEnabled(autoLateFeeEnabled);
            setting.setAutoLateFeePct(autoLateFeePct != null ? autoLateFeePct : BigDecimal.ZERO);
            setting.setAutoLateFeeAfterDays((short) clamp(autoLateFeeAfterDays, 0, 60, 5));
            setting.setQuietHoursStart((short) clamp(quietHoursStart, 0, 23, 21));
            setting.setQuietHoursEnd((short) clamp(quietHoursEnd, 0, 23, 8));

            validateSmtp(setting, locale);
            repository.save(setting);
            FlashAlert.ok(ra, msg(locale, "system.flash.saved"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/system-settings";
    }

    @PostMapping("/test-email")
    public String testEmail(@AuthenticationPrincipal AppUserDetails me,
                            @RequestParam(required = false) String testEmail,
                            Locale locale,
                            RedirectAttributes ra) {
        try {
            emailService.sendTest(me.getId(), clean(testEmail) != null ? testEmail : me.getEmail());
            FlashAlert.ok(ra, msg(locale, "system.flash.testSent"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/system-settings#smtp";
    }

    private AutomationSetting loadOrCreate(AppUserDetails me) {
        return repository.findByOwnerId(me.getId())
                .orElseGet(() -> repository.save(AutomationSetting.builder().ownerId(me.getId()).build()));
    }

    private void validateSmtp(AutomationSetting setting, Locale locale) {
        if (!setting.isSmtpEnabled()) return;
        if (clean(setting.getSmtpHost()) == null) {
            throw BusinessException.badRequest(msg(locale, "system.validation.smtpHostRequired"));
        }
        if (clean(setting.getSmtpFromEmail()) == null) {
            throw BusinessException.badRequest(msg(locale, "system.validation.fromEmailRequired"));
        }
        if (!isEmail(setting.getSmtpFromEmail())) {
            throw BusinessException.badRequest(msg(locale, "system.validation.fromEmailInvalid"));
        }
        if (setting.isSmtpAuth() && clean(setting.getSmtpUsername()) == null) {
            throw BusinessException.badRequest(msg(locale, "system.validation.smtpUsernameRequired"));
        }
        if (setting.isSmtpAuth() && clean(setting.getSmtpPassword()) == null) {
            throw BusinessException.badRequest(msg(locale, "system.validation.smtpPasswordRequired"));
        }
    }

    private static boolean isEmail(String value) {
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return true;
        } catch (AddressException ex) {
            return false;
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

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
