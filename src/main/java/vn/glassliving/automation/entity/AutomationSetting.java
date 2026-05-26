package vn.glassliving.automation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.glassliving.common.audit.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-owner automation rules: invoice creation, reminder cadence,
 * channels, contract renewal alert, late fees.
 */
@Entity
@Table(name = "automation_setting")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE automation_setting SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class AutomationSetting extends BaseEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    // ---------- Invoice ----------
    @Column(name = "invoice_auto_create", nullable = false)
    @Builder.Default
    private boolean invoiceAutoCreate = true;

    /** Day of month to issue invoice (1-28). */
    @Column(name = "invoice_create_day", nullable = false)
    @Builder.Default
    private Short invoiceCreateDay = 1;

    // ---------- Reminders ----------
    /** Comma-separated days BEFORE due date to remind, e.g. "7,3,1". */
    @Column(name = "reminder_pre_due_days", nullable = false, length = 40)
    @Builder.Default
    private String reminderPreDueDays = "7,3,1";

    /** Comma-separated days AFTER due date to remind, e.g. "1,3,7,14". */
    @Column(name = "reminder_overdue_days", nullable = false, length = 40)
    @Builder.Default
    private String reminderOverdueDays = "1,3,7,14";

    @Column(name = "reminder_channel_email", nullable = false)
    @Builder.Default
    private boolean reminderChannelEmail = true;

    @Column(name = "reminder_channel_sms", nullable = false)
    @Builder.Default
    private boolean reminderChannelSms = false;

    @Column(name = "reminder_channel_zalo", nullable = false)
    @Builder.Default
    private boolean reminderChannelZalo = false;

    // ---------- SMTP / Email delivery ----------
    @Column(name = "smtp_enabled", nullable = false)
    @Builder.Default
    private boolean smtpEnabled = false;

    @Column(name = "smtp_host", length = 180)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    @Builder.Default
    private Integer smtpPort = 587;

    @Column(name = "smtp_username", length = 180)
    private String smtpUsername;

    /** Stored as provided by the owner. For production, encrypt this column or move it to a secret vault. */
    @Column(name = "smtp_password", columnDefinition = "TEXT")
    private String smtpPassword;

    @Column(name = "smtp_from_email", length = 180)
    private String smtpFromEmail;

    @Column(name = "smtp_from_name", length = 120)
    private String smtpFromName;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(name = "contact_zalo", length = 32)
    private String contactZalo;

    @Column(name = "smtp_auth", nullable = false)
    @Builder.Default
    private boolean smtpAuth = true;

    @Column(name = "smtp_start_tls", nullable = false)
    @Builder.Default
    private boolean smtpStartTls = true;

    @Column(name = "smtp_ssl_trust", nullable = false)
    @Builder.Default
    private boolean smtpSslTrust = false;

    @Column(name = "invoice_email_enabled", nullable = false)
    @Builder.Default
    private boolean invoiceEmailEnabled = true;

    @Column(name = "payment_reminder_email_enabled", nullable = false)
    @Builder.Default
    private boolean paymentReminderEmailEnabled = true;

    @Column(name = "contract_expiry_email_enabled", nullable = false)
    @Builder.Default
    private boolean contractExpiryEmailEnabled = true;

    // ---------- Contract ----------
    /** Days before contract end_date to alert owner & tenant. */
    @Column(name = "contract_renew_alert_days", nullable = false)
    @Builder.Default
    private Short contractRenewAlertDays = 30;

    // ---------- Late fees ----------
    @Column(name = "auto_late_fee_enabled", nullable = false)
    @Builder.Default
    private boolean autoLateFeeEnabled = false;

    /** Percentage of invoice (e.g. 0.50 = 0.5%). */
    @Column(name = "auto_late_fee_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal autoLateFeePct = BigDecimal.ZERO;

    @Column(name = "auto_late_fee_after_days", nullable = false)
    @Builder.Default
    private Short autoLateFeeAfterDays = 5;

    // ---------- Maintenance ----------
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "auto_assign_default_assignee_id")
    private UUID autoAssignDefaultAssigneeId;

    // ---------- Quiet hours (do not send SMS at night) ----------
    @Column(name = "quiet_hours_start", nullable = false)
    @Builder.Default
    private Short quietHoursStart = 21;

    @Column(name = "quiet_hours_end", nullable = false)
    @Builder.Default
    private Short quietHoursEnd = 8;
}
