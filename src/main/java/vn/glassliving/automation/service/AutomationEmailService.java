package vn.glassliving.automation.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationEmailService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AutomationSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public void sendTest(UUID ownerId, String toEmail) {
        AutomationSetting setting = loadReadySetting(ownerId);
        String recipient = clean(toEmail);
        if (recipient == null) {
            throw BusinessException.badRequest("Vui lòng nhập email nhận thử.");
        }
        send(setting, recipient,
                "SmartRent - kiểm tra SMTP",
                """
                <p>Xin chào,</p>
                <p>Đây là email kiểm tra từ cấu hình SMTP của SmartRent.</p>
                <p>Nếu bạn nhận được email này, hệ thống có thể tự động gửi hóa đơn, nhắc thanh toán và nhắc hợp đồng sắp hết hạn.</p>
                """);
    }

    public boolean sendInvoiceIssued(Invoice invoice) {
        AutomationSetting setting = settingRepository.findByOwnerId(invoice.getOwnerId()).orElse(null);
        if (!mailUsable(setting) || !setting.isInvoiceEmailEnabled()) return false;

        User tenant = userRepository.findById(invoice.getTenantUserId()).orElse(null);
        if (tenant == null || clean(tenant.getEmail()) == null) return false;

        Room room = roomRepository.findById(invoice.getRoomId()).orElse(null);
        String subject = "Hóa đơn " + invoice.getCode() + " tháng " + invoice.getPeriodMonth() + "/" + invoice.getPeriodYear();
        String body = """
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Hóa đơn <strong>%s</strong> đã được phát hành%s.</p>
                <ul>
                  <li>Kỳ: <strong>%s/%s</strong></li>
                  <li>Hạn thanh toán: <strong>%s</strong></li>
                  <li>Tổng cần thanh toán: <strong>%s</strong></li>
                </ul>
                <p>Vui lòng đăng nhập SmartRent để xem chi tiết và thanh toán đúng hạn.</p>
                """.formatted(
                esc(tenant.getFullName()),
                esc(invoice.getCode()),
                room != null ? " cho phòng <strong>" + esc(room.getCode()) + "</strong>" : "",
                invoice.getPeriodMonth(),
                invoice.getPeriodYear(),
                DATE.format(invoice.getDueDate()),
                esc(MoneyFormatter.vnd(invoice.getTotalAmount()))
        );
        return sendQuietly(setting, tenant.getEmail(), subject, body);
    }

    public boolean sendPaymentReminder(Invoice invoice) {
        AutomationSetting setting = settingRepository.findByOwnerId(invoice.getOwnerId()).orElse(null);
        if (!mailUsable(setting) || !setting.isPaymentReminderEmailEnabled()) return false;

        User tenant = userRepository.findById(invoice.getTenantUserId()).orElse(null);
        if (tenant == null || clean(tenant.getEmail()) == null) return false;

        Room room = roomRepository.findById(invoice.getRoomId()).orElse(null);
        String subject = "Nhắc thanh toán hóa đơn " + invoice.getCode();
        String body = """
                <p>Xin chào <strong>%s</strong>,</p>
                <p>SmartRent gửi bạn nhắc thanh toán hóa đơn <strong>%s</strong>%s.</p>
                <ul>
                  <li>Hạn thanh toán: <strong>%s</strong></li>
                  <li>Số tiền cần thanh toán: <strong>%s</strong></li>
                  <li>Trạng thái: <strong>%s</strong></li>
                </ul>
                <p>Nếu bạn đã thanh toán, vui lòng bỏ qua email này hoặc liên hệ chủ trọ để cập nhật trạng thái.</p>
                """.formatted(
                esc(tenant.getFullName()),
                esc(invoice.getCode()),
                room != null ? " của phòng <strong>" + esc(room.getCode()) + "</strong>" : "",
                DATE.format(invoice.getDueDate()),
                esc(MoneyFormatter.vnd(invoice.getTotalAmount())),
                esc(statusLabel(invoice.getStatus()))
        );
        return sendQuietly(setting, tenant.getEmail(), subject, body);
    }

    public boolean sendContractExpiryReminder(Contract contract) {
        AutomationSetting setting = settingRepository.findByOwnerId(contract.getOwnerId()).orElse(null);
        if (!mailUsable(setting) || !setting.isContractExpiryEmailEnabled()) return false;

        User tenant = userRepository.findById(contract.getTenantUserId()).orElse(null);
        if (tenant == null || clean(tenant.getEmail()) == null) return false;

        Room room = roomRepository.findById(contract.getRoomId()).orElse(null);
        String subject = "Hợp đồng " + contract.getCode() + " sắp hết hạn";
        String body = """
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Hợp đồng <strong>%s</strong>%s sẽ kết thúc vào <strong>%s</strong>.</p>
                <p>Vui lòng trao đổi với chủ trọ nếu bạn muốn gia hạn hoặc cần hỗ trợ thủ tục trả phòng.</p>
                """.formatted(
                esc(tenant.getFullName()),
                esc(contract.getCode()),
                room != null ? " của phòng <strong>" + esc(room.getCode()) + "</strong>" : "",
                DATE.format(contract.getEndDate())
        );
        return sendQuietly(setting, tenant.getEmail(), subject, body);
    }

    public boolean mailUsable(AutomationSetting setting) {
        return setting != null
                && setting.isSmtpEnabled()
                && setting.isReminderChannelEmail()
                && clean(setting.getSmtpHost()) != null
                && setting.getSmtpPort() != null
                && setting.getSmtpPort() > 0
                && clean(setting.getSmtpFromEmail()) != null;
    }

    private AutomationSetting loadReadySetting(UUID ownerId) {
        AutomationSetting setting = settingRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> BusinessException.badRequest("Chưa có cấu hình tự động hóa."));
        if (!mailUsable(setting)) {
            throw BusinessException.badRequest("SMTP chưa sẵn sàng. Vui lòng bật SMTP, nhập host, port và email người gửi.");
        }
        return setting;
    }

    private boolean sendQuietly(AutomationSetting setting, String toEmail, String subject, String bodyHtml) {
        try {
            send(setting, toEmail, subject, bodyHtml);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send automation email to {} via owner {}", toEmail, setting.getOwnerId(), ex);
            return false;
        }
    }

    private void send(AutomationSetting setting, String toEmail, String subject, String bodyHtml) {
        try {
            JavaMailSenderImpl sender = senderFor(setting);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            InternetAddress from = new InternetAddress(
                    clean(setting.getSmtpFromEmail()),
                    Objects.toString(clean(setting.getSmtpFromName()), "SmartRent"),
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(clean(toEmail));
            helper.setSubject(subject);
            helper.setText(wrap(bodyHtml), true);
            sender.send(message);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("Không gửi được email: " + ex.getMessage());
        }
    }

    private JavaMailSenderImpl senderFor(AutomationSetting setting) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(clean(setting.getSmtpHost()));
        sender.setPort(Optional.ofNullable(setting.getSmtpPort()).orElse(587));
        if (setting.isSmtpAuth()) {
            sender.setUsername(clean(setting.getSmtpUsername()));
            sender.setPassword(Objects.toString(setting.getSmtpPassword(), ""));
        }
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(setting.isSmtpAuth()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(setting.isSmtpStartTls()));
        props.put("mail.smtp.starttls.required", Boolean.toString(setting.isSmtpStartTls()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (setting.isSmtpSslTrust()) {
            props.put("mail.smtp.ssl.trust", clean(setting.getSmtpHost()));
        }
        return sender;
    }

    private static String wrap(String body) {
        return """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;color:#0f172a;line-height:1.6;font-size:14px">
                  <div style="max-width:560px;margin:0 auto;padding:24px;border:1px solid #e5e7eb;border-radius:16px;background:#ffffff">
                    <div style="font-weight:800;font-size:18px;color:#4f46e5;margin-bottom:16px">SmartRent</div>
                    %s
                    <div style="margin-top:24px;padding-top:16px;border-top:1px solid #eef2f7;color:#64748b;font-size:12px">
                      Email tự động từ hệ thống SmartRent.
                    </div>
                  </div>
                </div>
                """.formatted(body);
    }

    private static String statusLabel(Invoice.InvoiceStatus status) {
        if (status == null) return "Chờ thanh toán";
        return switch (status) {
            case PENDING -> "Chờ thanh toán";
            case PARTIALLY_PAID -> "Thanh toán một phần";
            case PAID -> "Đã thanh toán";
            case OVERDUE -> "Quá hạn";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(Objects.toString(value, ""));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isBlank() ? null : s;
    }
}
