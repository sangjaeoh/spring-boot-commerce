ALTER TABLE ordering.orders
    ADD COLUMN carrier         VARCHAR(100),
    ADD COLUMN tracking_number VARCHAR(100);
