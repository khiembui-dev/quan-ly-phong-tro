-- Conversation messages for maintenance/support tickets.

CREATE TABLE IF NOT EXISTS maintenance_ticket_message (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES maintenance_ticket(id),
    sender_user_id UUID,
    sender_role VARCHAR(20) NOT NULL,
    body TEXT,
    photo_urls TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_mtm_ticket_created
    ON maintenance_ticket_message(ticket_id, created_at);

CREATE INDEX IF NOT EXISTS idx_mtm_sender
    ON maintenance_ticket_message(sender_user_id);
