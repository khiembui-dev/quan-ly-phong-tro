-- ===================================================================
-- Glass Living - V5 partial unique slug
-- Soft-deleted rows (deleted=true) should not block reuse of their slug
-- when the user creates a new property/room. Convert the global UNIQUE
-- constraint into a partial unique index that ignores soft-deleted rows.
-- ===================================================================

ALTER TABLE property DROP CONSTRAINT IF EXISTS property_slug_key;
CREATE UNIQUE INDEX property_slug_active_uk ON property (slug) WHERE deleted = false;

ALTER TABLE room DROP CONSTRAINT IF EXISTS room_slug_key;
CREATE UNIQUE INDEX room_slug_active_uk ON room (slug) WHERE deleted = false;
