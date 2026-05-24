-- ===================================================================
-- Glass Living - V7 add room.current_tenant_id
-- Source of truth for who's currently renting the room. Synced from
-- the active contract; nullable when room is AVAILABLE.
-- ===================================================================
ALTER TABLE room
    ADD COLUMN current_tenant_id UUID NULL REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_room_current_tenant ON room(current_tenant_id) WHERE current_tenant_id IS NOT NULL;
