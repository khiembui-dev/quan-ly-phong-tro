-- Bank transfer reconciliation audit fields for automatic invoice payment.
ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS payment_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS bank_transaction_number VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bank_description TEXT;

ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS payment_code VARCHAR(160);

UPDATE payment
SET status = 'PAID'
WHERE status = 'SUCCESS';

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_bank_transaction_number
    ON payment(bank_transaction_number)
    WHERE bank_transaction_number IS NOT NULL AND deleted = false;
