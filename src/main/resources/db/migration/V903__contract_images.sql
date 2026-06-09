CREATE TABLE IF NOT EXISTS contract_image (
    id          UUID PRIMARY KEY,
    contract_id UUID NOT NULL,
    url         TEXT NOT NULL,
    alt         VARCHAR(200),
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contract_image_contract FOREIGN KEY (contract_id) REFERENCES contract(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contract_image_contract ON contract_image(contract_id, sort_order);

INSERT INTO contract_image (id, contract_id, url, alt, sort_order, created_at)
SELECT gen_random_uuid(), c.id, c.contract_image_url, c.code, 0, COALESCE(c.created_at, CURRENT_TIMESTAMP)
FROM contract c
WHERE c.contract_image_url IS NOT NULL
  AND c.contract_image_url <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM contract_image ci
      WHERE ci.contract_id = c.id
        AND ci.url = c.contract_image_url
  );
