ALTER TABLE ordering.orders
    ADD COLUMN refunded_at   TIMESTAMPTZ,
    ADD COLUMN refund_reason VARCHAR(30);
