-- ===================================================================
-- Glass Living - V8 add room.inherit_tariff
-- When TRUE (default), room ignores its own electric/water unit and
-- inherits from its property. When FALSE, room values are used.
-- ===================================================================
ALTER TABLE room
    ADD COLUMN inherit_tariff BOOLEAN NOT NULL DEFAULT TRUE;
