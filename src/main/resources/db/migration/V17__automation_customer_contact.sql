ALTER TABLE automation_setting
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(180),
    ADD COLUMN IF NOT EXISTS contact_zalo VARCHAR(32);
