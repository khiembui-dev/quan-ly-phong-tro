package vn.glassliving.automation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationScheduler {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<Invoice.InvoiceStatus> OPEN_INVOICE_STATUSES = List.of(
            Invoice.InvoiceStatus.PENDING,
            Invoice.InvoiceStatus.PARTIALLY_PAID,
            Invoice.InvoiceStatus.OVERDUE
    );

    private final AutomationSettingRepository settingRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final AutomationEmailService emailService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void runDailyEmailAutomations() {
        LocalDate today = LocalDate.now(VIETNAM);
        for (AutomationSetting setting : settingRepository.findAll()) {
            if (!emailService.mailUsable(setting)) {
                continue;
            }
            runForOwner(setting, today);
        }
    }

    @Transactional
    public int runContractExpiryNow(UUID ownerId) {
        AutomationSetting setting = settingRepository.findByOwnerId(ownerId).orElse(null);
        if (!emailService.mailUsable(setting) || !setting.isContractExpiryEmailEnabled()) {
            return 0;
        }
        return sendContractExpiry(setting, LocalDate.now(VIETNAM));
    }

    private void runForOwner(AutomationSetting setting, LocalDate today) {
        if (setting.isContractExpiryEmailEnabled()) {
            sendContractExpiry(setting, today);
        }
        if (setting.isPaymentReminderEmailEnabled()) {
            sendPaymentReminders(setting, today);
        }
    }

    private int sendContractExpiry(AutomationSetting setting, LocalDate today) {
        int days = clamp(setting.getContractRenewAlertDays(), 1, 180, 30);
        LocalDate targetDate = today.plusDays(days);
        int sent = 0;
        for (Contract contract : contractRepository.findByOwnerIdAndStatusAndEndDate(
                setting.getOwnerId(), Contract.ContractStatus.ACTIVE, targetDate)) {
            if (emailService.sendContractExpiryReminder(contract)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Sent {} contract expiry emails for owner {}", sent, setting.getOwnerId());
        }
        return sent;
    }

    private void sendPaymentReminders(AutomationSetting setting, LocalDate today) {
        Set<LocalDate> targetDates = new LinkedHashSet<>();
        for (int day : parseDays(setting.getReminderPreDueDays())) {
            targetDates.add(today.plusDays(day));
        }
        for (int day : parseDays(setting.getReminderOverdueDays())) {
            targetDates.add(today.minusDays(day));
        }

        int sent = 0;
        OffsetDateTime now = OffsetDateTime.now(VIETNAM);
        for (LocalDate dueDate : targetDates) {
            for (Invoice invoice : invoiceRepository.findByOwnerIdAndStatusInAndDueDate(
                    setting.getOwnerId(), OPEN_INVOICE_STATUSES, dueDate)) {
                if (alreadyRemindedToday(invoice, today)) {
                    continue;
                }
                if (emailService.sendPaymentReminder(invoice)) {
                    invoice.setLastReminderAt(now);
                    invoiceRepository.save(invoice);
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Sent {} payment reminder emails for owner {}", sent, setting.getOwnerId());
        }
    }

    private static boolean alreadyRemindedToday(Invoice invoice, LocalDate today) {
        return invoice.getLastReminderAt() != null
                && invoice.getLastReminderAt().atZoneSameInstant(VIETNAM).toLocalDate().equals(today);
    }

    private static Set<Integer> parseDays(String value) {
        Set<Integer> days = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return days;
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(s -> {
                    try {
                        int n = Integer.parseInt(s);
                        if (n >= 0 && n <= 365) days.add(n);
                    } catch (NumberFormatException ignored) {
                    }
                });
        return days;
    }

    private static int clamp(Short value, int min, int max, int fallback) {
        int n = value != null ? value : fallback;
        return Math.max(min, Math.min(max, n));
    }
}
