CREATE TABLE password_reset_code (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    email           VARCHAR(160) NOT NULL,
    code_hash       VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at     TIMESTAMP WITH TIME ZONE,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_password_reset_email_active ON password_reset_code(email, consumed_at, expires_at);
CREATE INDEX idx_password_reset_user ON password_reset_code(user_id);
