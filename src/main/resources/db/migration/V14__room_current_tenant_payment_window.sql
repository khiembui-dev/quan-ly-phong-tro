-- ===================================================================
-- Glass Living - V14 current tenant payment window
-- Tracks the current tenant's room usage/payment coverage directly on
-- the room assignment. This keeps the admin tenant/room screens fast and
-- works for both contract-backed and manually assigned tenants.
-- ===================================================================
ALTER TABLE room
    ADD COLUMN current_tenant_started_on DATE NULL,
    ADD COLUMN current_tenant_paid_until DATE NULL;

UPDATE room r
SET current_tenant_started_on = COALESCE(
        (
            SELECT c.start_date
            FROM contract c
            WHERE c.room_id = r.id
              AND c.tenant_user_id = r.current_tenant_id
              AND c.status = 'ACTIVE'
              AND c.deleted = FALSE
            ORDER BY c.start_date DESC
            LIMIT 1
        ),
        CURRENT_DATE
    )
WHERE r.current_tenant_id IS NOT NULL
  AND r.current_tenant_started_on IS NULL;

UPDATE room r
SET current_tenant_paid_until = COALESCE(
        (
            SELECT (make_date(i.period_year::int, i.period_month::int, 1) + INTERVAL '1 month - 1 day')::date
            FROM invoice i
            WHERE i.room_id = r.id
              AND i.tenant_user_id = r.current_tenant_id
              AND i.status = 'PAID'
              AND i.deleted = FALSE
            ORDER BY i.period_year DESC, i.period_month DESC
            LIMIT 1
        ),
        (
            SELECT (c.start_date + INTERVAL '1 month - 1 day')::date
            FROM contract c
            WHERE c.room_id = r.id
              AND c.tenant_user_id = r.current_tenant_id
              AND c.status = 'ACTIVE'
              AND c.deleted = FALSE
            ORDER BY c.start_date DESC
            LIMIT 1
        ),
        (CURRENT_DATE + INTERVAL '30 days')::date
    )
WHERE r.current_tenant_id IS NOT NULL
  AND r.current_tenant_paid_until IS NULL;

CREATE INDEX idx_room_current_tenant_paid_until
    ON room(current_tenant_paid_until)
    WHERE current_tenant_id IS NOT NULL;
