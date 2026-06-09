-- Track an admin broadcast independently from each recipient notification.

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS batch_id UUID,
    ADD COLUMN IF NOT EXISTS sender_user_id UUID;

CREATE INDEX IF NOT EXISTS idx_notification_sender_created
    ON notification (sender_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_batch
    ON notification (batch_id);
