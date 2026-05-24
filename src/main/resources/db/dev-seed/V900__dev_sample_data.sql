-- ===================================================================
-- DEV-only seed data. Loaded only in profile=dev.
-- NOTE: User accounts (owner@/tenant@/admin@) are seeded by
-- DevDataInitializer.java via the real BCrypt encoder, NOT this SQL.
-- This file only seeds reference data (properties, rooms, etc.).
-- DevDataInitializer also creates Owner profile + sample contracts/invoices/favorites/notifications
-- if no users exist yet on first boot.
-- ===================================================================

-- (Intentionally empty — moved to DevDataInitializer.java)
SELECT 1;
