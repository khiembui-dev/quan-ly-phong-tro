-- ===================================================================
-- Glass Living - V9 add room.extra_fees JSONB
-- List of {name, amount} for parking, elevator, internet, etc.
-- Inherited from property at room-creation time, but editable per room.
-- ===================================================================
ALTER TABLE room
    ADD COLUMN extra_fees JSONB NOT NULL DEFAULT '[]'::jsonb;
