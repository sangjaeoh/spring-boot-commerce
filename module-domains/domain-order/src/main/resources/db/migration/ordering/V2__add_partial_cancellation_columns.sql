ALTER TABLE ordering.orders
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ordering.order_line
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    ADD COLUMN refund_amount BIGINT;
