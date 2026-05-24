-- ===================================================================
-- Glass Living - V13 add room.custom_amenities
-- Per-room user-defined amenities outside the seeded amenity catalog.
-- Stored as JSONB list of {name, category}.
-- ===================================================================
ALTER TABLE room
    ADD COLUMN custom_amenities JSONB NOT NULL DEFAULT '[]'::jsonb;
