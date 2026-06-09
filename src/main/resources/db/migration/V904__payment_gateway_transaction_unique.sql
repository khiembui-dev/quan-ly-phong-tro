-- Prevent a bank transaction from being credited to more than one payment.
ALTER TABLE payment
    ADD CONSTRAINT uq_payment_gateway_txn UNIQUE (gateway_txn_id);
