-- SmartRent - free-form room facts shown as room information chips.

ALTER TABLE room
    ADD COLUMN IF NOT EXISTS room_info JSONB NOT NULL DEFAULT '[]'::jsonb;
