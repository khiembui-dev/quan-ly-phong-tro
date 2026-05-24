-- ===================================================================
-- Glass Living - Automation email settings
-- Per-owner SMTP and email automation toggles.
-- ===================================================================

ALTER TABLE automation_setting ADD COLUMN smtp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE automation_setting ADD COLUMN smtp_host VARCHAR(180);
ALTER TABLE automation_setting ADD COLUMN smtp_port INTEGER NOT NULL DEFAULT 587;
ALTER TABLE automation_setting ADD COLUMN smtp_username VARCHAR(180);
ALTER TABLE automation_setting ADD COLUMN smtp_password TEXT;
ALTER TABLE automation_setting ADD COLUMN smtp_from_email VARCHAR(180);
ALTER TABLE automation_setting ADD COLUMN smtp_from_name VARCHAR(120);
ALTER TABLE automation_setting ADD COLUMN smtp_auth BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation_setting ADD COLUMN smtp_start_tls BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation_setting ADD COLUMN smtp_ssl_trust BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE automation_setting ADD COLUMN invoice_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation_setting ADD COLUMN payment_reminder_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation_setting ADD COLUMN contract_expiry_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
