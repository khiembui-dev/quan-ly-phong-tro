-- ===================================================================
-- Glass Living - V6 partial unique index on room (property_id, code)
-- Two rooms in different properties may share a code, but within one
-- property the code must be unique among non-soft-deleted rows.
-- ===================================================================
CREATE UNIQUE INDEX room_code_per_property_active_uk
    ON room (property_id, code)
    WHERE deleted = false;
