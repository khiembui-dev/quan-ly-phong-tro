ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS permanent_address TEXT,
    ADD COLUMN IF NOT EXISTS identity_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS identity_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS identity_issued_date DATE,
    ADD COLUMN IF NOT EXISTS identity_issued_place VARCHAR(160),
    ADD COLUMN IF NOT EXISTS identity_front_url TEXT,
    ADD COLUMN IF NOT EXISTS identity_back_url TEXT,
    ADD COLUMN IF NOT EXISTS identity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS identity_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS identity_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS identity_verified_by UUID;

CREATE INDEX IF NOT EXISTS idx_user_identity_number ON app_user(identity_number);
