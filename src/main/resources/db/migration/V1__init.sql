-- ===================================================================
-- Glass Living — V1 init schema (Postgres + H2/PostgreSQL-compat mode)
-- ===================================================================
-- Note: H2 with MODE=PostgreSQL supports gen_random_uuid() since 1.4.x.
-- pgcrypto / pg_trgm extensions only loaded on real Postgres (skip on H2).

CREATE TABLE app_user (
    id                 UUID         PRIMARY KEY,
    email              VARCHAR(160) NOT NULL UNIQUE,
    email_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    phone              VARCHAR(20)  UNIQUE,
    phone_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash      VARCHAR(100),
    full_name          VARCHAR(120) NOT NULL,
    avatar_url         TEXT,
    gender             VARCHAR(16),
    dob                DATE,
    locale             VARCHAR(8)   NOT NULL DEFAULT 'vi-VN',
    status             VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    two_factor_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    two_factor_secret  VARCHAR(64),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_user_status ON app_user(status);
-- Note: functional index on LOWER(email) skipped for H2 compatibility.
-- The UNIQUE constraint on app_user.email already provides exact-match index.

CREATE TABLE user_role (
    user_id UUID NOT NULL,
    role    VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- =====================================================================
-- TENANT / OWNER profiles (extension of User)
-- =====================================================================
CREATE TABLE tenant_profile (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL UNIQUE,
    cccd_number_enc     TEXT,
    cccd_front_url      TEXT,
    cccd_back_url       TEXT,
    permanent_address   VARCHAR(240),
    occupation          VARCHAR(120),
    workplace           VARCHAR(200),
    blacklisted         BOOLEAN NOT NULL DEFAULT FALSE,
    internal_note       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tenant_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE owner_profile (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL UNIQUE,
    business_name       VARCHAR(200),
    tax_code            VARCHAR(40),
    bank_name           VARCHAR(80),
    bank_account_no     VARCHAR(40),
    bank_account_name   VARCHAR(120),
    verified            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_owner_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- =====================================================================
-- PROPERTY (cơ sở nhà trọ)
-- =====================================================================
CREATE TABLE property (
    id           UUID PRIMARY KEY,
    owner_id     UUID NOT NULL,
    name         VARCHAR(160) NOT NULL,
    slug         VARCHAR(160) NOT NULL UNIQUE,
    description  TEXT,
    address_line VARCHAR(240) NOT NULL,
    district     VARCHAR(80)  NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    lat          NUMERIC(9,6),
    lng          NUMERIC(9,6),
    cover_url    TEXT,
    floor_plan_svg TEXT,
    total_rooms  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_property_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
CREATE INDEX idx_property_owner    ON property(owner_id);
CREATE INDEX idx_property_district ON property(district, city);

-- =====================================================================
-- AMENITY (n-n with Room)
-- =====================================================================
CREATE TABLE amenity (
    id    UUID PRIMARY KEY,
    code  VARCHAR(40)  NOT NULL UNIQUE,
    name  VARCHAR(120) NOT NULL,
    icon  VARCHAR(40),
    sort_order INTEGER NOT NULL DEFAULT 0
);

-- =====================================================================
-- ROOM
-- =====================================================================
CREATE TABLE room (
    id              UUID PRIMARY KEY,
    property_id     UUID NOT NULL,
    owner_id        UUID NOT NULL,
    code            VARCHAR(32)  NOT NULL,
    slug            VARCHAR(160) NOT NULL UNIQUE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    type            VARCHAR(24)  NOT NULL,
    floor           SMALLINT,
    area_sqm        NUMERIC(6,2) NOT NULL,
    bedrooms        SMALLINT     NOT NULL DEFAULT 1,
    bathrooms       SMALLINT     NOT NULL DEFAULT 1,
    max_occupants   SMALLINT     NOT NULL DEFAULT 2,
    price_monthly   NUMERIC(14,0) NOT NULL,
    deposit_amount  NUMERIC(14,0) NOT NULL,
    service_fee     NUMERIC(14,0) NOT NULL DEFAULT 0,
    electric_unit   NUMERIC(8,0)  NOT NULL DEFAULT 4000,
    water_unit      NUMERIC(8,0)  NOT NULL DEFAULT 25000,
    status          VARCHAR(24)  NOT NULL DEFAULT 'AVAILABLE',
    pet_allowed     BOOLEAN      NOT NULL DEFAULT FALSE,
    has_balcony     BOOLEAN      NOT NULL DEFAULT FALSE,
    address_line    VARCHAR(240),
    district        VARCHAR(80)  NOT NULL,
    city            VARCHAR(80)  NOT NULL,
    lat             NUMERIC(9,6),
    lng             NUMERIC(9,6),
    rating_avg      NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count    INTEGER      NOT NULL DEFAULT 0,
    view_count      INTEGER      NOT NULL DEFAULT 0,
    cover_url       TEXT,
    published_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_room_property FOREIGN KEY (property_id) REFERENCES property(id),
    CONSTRAINT fk_room_owner    FOREIGN KEY (owner_id)    REFERENCES app_user(id),
    CONSTRAINT uq_room_code     UNIQUE (property_id, code)
);
CREATE INDEX idx_room_status   ON room(status);
CREATE INDEX idx_room_district ON room(district, price_monthly);
CREATE INDEX idx_room_owner    ON room(owner_id);

CREATE TABLE room_image (
    id          UUID PRIMARY KEY,
    room_id     UUID NOT NULL,
    url         TEXT NOT NULL,
    alt         VARCHAR(200),
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_cover    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_image_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE
);
CREATE INDEX idx_image_room ON room_image(room_id);

CREATE TABLE room_amenity (
    room_id    UUID NOT NULL,
    amenity_id UUID NOT NULL,
    PRIMARY KEY (room_id, amenity_id),
    CONSTRAINT fk_ra_room    FOREIGN KEY (room_id)    REFERENCES room(id)    ON DELETE CASCADE,
    CONSTRAINT fk_ra_amenity FOREIGN KEY (amenity_id) REFERENCES amenity(id) ON DELETE CASCADE
);

-- =====================================================================
-- FAVORITE
-- =====================================================================
CREATE TABLE favorite (
    user_id    UUID NOT NULL,
    room_id    UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, room_id),
    CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_fav_room FOREIGN KEY (room_id) REFERENCES room(id)     ON DELETE CASCADE
);

-- =====================================================================
-- REVIEW
-- =====================================================================
CREATE TABLE review (
    id          UUID PRIMARY KEY,
    room_id     UUID NOT NULL,
    user_id     UUID NOT NULL,
    rating      SMALLINT NOT NULL,
    title       VARCHAR(200),
    body        TEXT,
    owner_reply TEXT,
    owner_replied_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_rev_room FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_rev_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_rev_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_review_room ON review(room_id);

-- =====================================================================
-- BOOKING
-- =====================================================================
CREATE TABLE booking (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    tenant_user_id  UUID NOT NULL,
    room_id         UUID NOT NULL,
    owner_id        UUID NOT NULL,
    status          VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    cccd_number_enc TEXT,
    cccd_front_url  TEXT,
    cccd_back_url   TEXT,
    occupation      VARCHAR(120),
    move_in_date    DATE        NOT NULL,
    duration_months SMALLINT    NOT NULL,
    occupant_count  SMALLINT    NOT NULL DEFAULT 1,
    has_pet         BOOLEAN     NOT NULL DEFAULT FALSE,
    note            TEXT,
    rent_monthly    NUMERIC(14,0) NOT NULL,
    deposit_amount  NUMERIC(14,0) NOT NULL,
    service_fee     NUMERIC(14,0) NOT NULL DEFAULT 0,
    total_due       NUMERIC(14,0) NOT NULL,
    payment_method  VARCHAR(24),
    payment_id      UUID,
    paid_at         TIMESTAMP WITH TIME ZONE,
    expired_at      TIMESTAMP WITH TIME ZONE,
    contract_id     UUID,
    confirmed_at    TIMESTAMP WITH TIME ZONE,
    cancelled_at    TIMESTAMP WITH TIME ZONE,
    cancel_reason   VARCHAR(240),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_booking_tenant FOREIGN KEY (tenant_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_booking_room   FOREIGN KEY (room_id)        REFERENCES room(id),
    CONSTRAINT fk_booking_owner  FOREIGN KEY (owner_id)       REFERENCES app_user(id)
);
CREATE INDEX idx_booking_tenant ON booking(tenant_user_id, status);
CREATE INDEX idx_booking_room   ON booking(room_id, status);
CREATE INDEX idx_booking_owner  ON booking(owner_id, status);

-- =====================================================================
-- CONTRACT
-- =====================================================================
CREATE TABLE contract (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    booking_id      UUID UNIQUE,
    room_id         UUID NOT NULL,
    owner_id        UUID NOT NULL,
    tenant_user_id  UUID NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    duration_months SMALLINT NOT NULL,
    rent_monthly    NUMERIC(14,0) NOT NULL,
    deposit_amount  NUMERIC(14,0) NOT NULL,
    service_fee     NUMERIC(14,0) NOT NULL DEFAULT 0,
    electric_unit   NUMERIC(8,0) NOT NULL,
    water_unit      NUMERIC(8,0) NOT NULL,
    billing_day     SMALLINT NOT NULL DEFAULT 1,
    status          VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    pdf_url         TEXT,
    owner_signed_at TIMESTAMP WITH TIME ZONE,
    tenant_signed_at TIMESTAMP WITH TIME ZONE,
    signature_otp_hash VARCHAR(120),
    terminated_at   TIMESTAMP WITH TIME ZONE,
    terminated_reason VARCHAR(240),
    early_termination_fee NUMERIC(14,0),
    extra_terms     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_contract_room   FOREIGN KEY (room_id)        REFERENCES room(id),
    CONSTRAINT fk_contract_owner  FOREIGN KEY (owner_id)       REFERENCES app_user(id),
    CONSTRAINT fk_contract_tenant FOREIGN KEY (tenant_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_contract_booking FOREIGN KEY (booking_id)    REFERENCES booking(id),
    CONSTRAINT chk_contract_dates CHECK (end_date > start_date)
);
CREATE INDEX idx_contract_owner    ON contract(owner_id, status);
CREATE INDEX idx_contract_tenant   ON contract(tenant_user_id, status);
CREATE INDEX idx_contract_room     ON contract(room_id);
CREATE INDEX idx_contract_expiring ON contract(end_date);

-- =====================================================================
-- INVOICE
-- =====================================================================
CREATE TABLE invoice (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    contract_id     UUID NOT NULL,
    owner_id        UUID NOT NULL,
    tenant_user_id  UUID NOT NULL,
    room_id         UUID NOT NULL,
    period_year     SMALLINT NOT NULL,
    period_month    SMALLINT NOT NULL,
    issue_date      DATE NOT NULL,
    due_date        DATE NOT NULL,
    rent_amount     NUMERIC(14,0) NOT NULL,
    service_amount  NUMERIC(14,0) NOT NULL DEFAULT 0,
    electric_prev   NUMERIC(10,0),
    electric_curr   NUMERIC(10,0),
    electric_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    water_prev      NUMERIC(10,0),
    water_curr      NUMERIC(10,0),
    water_amount    NUMERIC(14,0) NOT NULL DEFAULT 0,
    other_items     TEXT,
    other_amount    NUMERIC(14,0) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    late_fee_amount NUMERIC(14,0) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14,0) NOT NULL,
    paid_amount     NUMERIC(14,0) NOT NULL DEFAULT 0,
    status          VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    paid_at         TIMESTAMP WITH TIME ZONE,
    last_reminder_at TIMESTAMP WITH TIME ZONE,
    pdf_url         TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_invoice_contract FOREIGN KEY (contract_id) REFERENCES contract(id),
    CONSTRAINT fk_invoice_owner    FOREIGN KEY (owner_id)    REFERENCES app_user(id),
    CONSTRAINT fk_invoice_tenant   FOREIGN KEY (tenant_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_invoice_room     FOREIGN KEY (room_id)     REFERENCES room(id),
    CONSTRAINT uq_invoice_period   UNIQUE (contract_id, period_year, period_month),
    CONSTRAINT chk_invoice_month   CHECK (period_month BETWEEN 1 AND 12)
);
CREATE INDEX idx_invoice_owner_status ON invoice(owner_id, status, due_date);
CREATE INDEX idx_invoice_tenant       ON invoice(tenant_user_id, status);

-- =====================================================================
-- PAYMENT
-- =====================================================================
CREATE TABLE payment (
    id              UUID PRIMARY KEY,
    code            VARCHAR(40) NOT NULL UNIQUE,
    invoice_id      UUID,
    booking_id      UUID,
    user_id         UUID NOT NULL,
    amount          NUMERIC(14,0) NOT NULL,
    method          VARCHAR(24)  NOT NULL,
    gateway_txn_id  VARCHAR(120),
    gateway_payload TEXT,
    status          VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    note            VARCHAR(240),
    paid_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_payment_invoice ON payment(invoice_id);
CREATE INDEX idx_payment_booking ON payment(booking_id);
CREATE INDEX idx_payment_user    ON payment(user_id, status);

-- =====================================================================
-- CHAT
-- =====================================================================
CREATE TABLE conversation (
    id          UUID PRIMARY KEY,
    title       VARCHAR(160),
    context_room_id UUID,
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE conversation_member (
    conversation_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    last_read_at    TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cm_conv FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE message (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id       UUID NOT NULL,
    body            TEXT,
    attachment_url  TEXT,
    type            VARCHAR(24) NOT NULL DEFAULT 'TEXT',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_msg_conv   FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES app_user(id)
);
CREATE INDEX idx_msg_conv_created ON message(conversation_id, created_at);

-- =====================================================================
-- NOTIFICATION
-- =====================================================================
CREATE TABLE notification (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    link_url    VARCHAR(400),
    read_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_notif_user ON notification(user_id, read_at);

-- =====================================================================
-- AI QUERY HISTORY
-- =====================================================================
CREATE TABLE ai_query (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    prompt      TEXT NOT NULL,
    response    TEXT,
    model       VARCHAR(80),
    tokens_in   INTEGER,
    tokens_out  INTEGER,
    duration_ms INTEGER,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_user_created ON ai_query(user_id, created_at);

-- =====================================================================
-- AUDIT LOG
-- =====================================================================
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(80) NOT NULL,
    entity      VARCHAR(80),
    entity_id   UUID,
    payload     TEXT,
    ip          VARCHAR(60),
    user_agent  VARCHAR(240),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_actor   ON audit_log(actor_id, created_at);
CREATE INDEX idx_audit_entity  ON audit_log(entity, entity_id);
