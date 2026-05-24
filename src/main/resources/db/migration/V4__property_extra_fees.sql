-- ===================================================================
-- Glass Living - V4 add property.extra_fees (JSONB list of {name, amount})
-- ===================================================================
ALTER TABLE property
    ADD COLUMN extra_fees JSONB NOT NULL DEFAULT '[]'::jsonb;
