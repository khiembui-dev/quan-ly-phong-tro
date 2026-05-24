-- ===================================================================
-- Glass Living - V3 admin extensions (2026)
-- Property tariff defaults, utility readings, maintenance, automation
-- ===================================================================

-- ---------------------------------------------------------------
-- PROPERTY: add tariff defaults (one ALTER per column for H2 compat)
-- ---------------------------------------------------------------
ALTER TABLE property ADD COLUMN electric_unit       NUMERIC(8,0)  NOT NULL DEFAULT 4000;
ALTER TABLE property ADD COLUMN water_unit          NUMERIC(8,0)  NOT NULL DEFAULT 25000;
ALTER TABLE property ADD COLUMN service_fee_default NUMERIC(14,0) NOT NULL DEFAULT 0;
ALTER TABLE property ADD COLUMN internet_fee        NUMERIC(14,0) NOT NULL DEFAULT 0;
ALTER TABLE property ADD COLUMN garbage_fee         NUMERIC(14,0) NOT NULL DEFAULT 0;
ALTER TABLE property ADD COLUMN management_fee      NUMERIC(14,0) NOT NULL DEFAULT 0;
ALTER TABLE property ADD COLUMN billing_day_default SMALLINT      NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------
-- UTILITY READING
-- ---------------------------------------------------------------
CREATE TABLE utility_reading (
    id                  UUID PRIMARY KEY,
    owner_id            UUID NOT NULL,
    property_id         UUID NOT NULL,
    room_id             UUID NOT NULL,
    period_year         SMALLINT NOT NULL,
    period_month        SMALLINT NOT NULL,
    reading_date        DATE NOT NULL,
    electric_prev       NUMERIC(10,2) NOT NULL DEFAULT 0,
    electric_curr       NUMERIC(10,2) NOT NULL DEFAULT 0,
    electric_unit_price NUMERIC(8,0)  NOT NULL DEFAULT 4000,
    electric_amount     NUMERIC(14,0) NOT NULL DEFAULT 0,
    water_prev          NUMERIC(10,2) NOT NULL DEFAULT 0,
    water_curr          NUMERIC(10,2) NOT NULL DEFAULT 0,
    water_unit_price    NUMERIC(8,0)  NOT NULL DEFAULT 25000,
    water_amount        NUMERIC(14,0) NOT NULL DEFAULT 0,
    note                TEXT,
    locked              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_ur_owner    FOREIGN KEY (owner_id)    REFERENCES app_user(id),
    CONSTRAINT fk_ur_property FOREIGN KEY (property_id) REFERENCES property(id),
    CONSTRAINT fk_ur_room     FOREIGN KEY (room_id)     REFERENCES room(id),
    CONSTRAINT uq_ur_room_period UNIQUE (room_id, period_year, period_month)
);
CREATE INDEX idx_ur_owner_period ON utility_reading(owner_id, period_year, period_month);
CREATE INDEX idx_ur_property     ON utility_reading(property_id);

-- ---------------------------------------------------------------
-- MAINTENANCE TICKET
-- ---------------------------------------------------------------
CREATE TABLE maintenance_ticket (
    id                UUID PRIMARY KEY,
    code              VARCHAR(32)  NOT NULL UNIQUE,
    owner_id          UUID NOT NULL,
    property_id       UUID,
    room_id           UUID,
    reporter_user_id  UUID,
    assignee_user_id  UUID,
    category          VARCHAR(40)  NOT NULL,
    priority          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    status            VARCHAR(24)  NOT NULL DEFAULT 'OPEN',
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    estimated_cost    NUMERIC(14,0),
    actual_cost       NUMERIC(14,0),
    photo_urls        TEXT,
    reported_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_for     TIMESTAMP WITH TIME ZONE,
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolution_note   TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_mt_owner    FOREIGN KEY (owner_id)    REFERENCES app_user(id),
    CONSTRAINT fk_mt_property FOREIGN KEY (property_id) REFERENCES property(id),
    CONSTRAINT fk_mt_room     FOREIGN KEY (room_id)     REFERENCES room(id),
    CONSTRAINT fk_mt_reporter FOREIGN KEY (reporter_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_mt_assignee FOREIGN KEY (assignee_user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_mt_owner_status ON maintenance_ticket(owner_id, status);
CREATE INDEX idx_mt_room         ON maintenance_ticket(room_id);
CREATE INDEX idx_mt_priority     ON maintenance_ticket(priority, status);

-- ---------------------------------------------------------------
-- AUTOMATION SETTING (one row per owner)
-- ---------------------------------------------------------------
CREATE TABLE automation_setting (
    id                              UUID PRIMARY KEY,
    owner_id                        UUID NOT NULL UNIQUE,
    invoice_auto_create             BOOLEAN NOT NULL DEFAULT TRUE,
    invoice_create_day              SMALLINT NOT NULL DEFAULT 1,
    reminder_pre_due_days           VARCHAR(40) NOT NULL DEFAULT '7,3,1',
    reminder_overdue_days           VARCHAR(40) NOT NULL DEFAULT '1,3,7,14',
    reminder_channel_email          BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_channel_sms            BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_channel_zalo           BOOLEAN NOT NULL DEFAULT FALSE,
    contract_renew_alert_days       SMALLINT NOT NULL DEFAULT 30,
    auto_late_fee_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    auto_late_fee_pct               NUMERIC(5,2) NOT NULL DEFAULT 0,
    auto_late_fee_after_days        SMALLINT NOT NULL DEFAULT 5,
    auto_assign_default_assignee_id UUID,
    quiet_hours_start               SMALLINT NOT NULL DEFAULT 21,
    quiet_hours_end                 SMALLINT NOT NULL DEFAULT 8,
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                      UUID,
    updated_by                      UUID,
    version                         BIGINT NOT NULL DEFAULT 0,
    deleted                         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_as_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
