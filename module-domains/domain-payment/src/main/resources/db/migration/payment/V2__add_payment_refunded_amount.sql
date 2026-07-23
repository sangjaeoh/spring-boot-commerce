ALTER TABLE payment.payment
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;
