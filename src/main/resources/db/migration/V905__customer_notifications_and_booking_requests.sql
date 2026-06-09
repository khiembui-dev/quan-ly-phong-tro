-- Customer-facing notifications can include one image, and booking requests keep the contact data submitted by tenants.

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS image_url TEXT;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS contact_full_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(180),
    ADD COLUMN IF NOT EXISTS contact_address TEXT;
