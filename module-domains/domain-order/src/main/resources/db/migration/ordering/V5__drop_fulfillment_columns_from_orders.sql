-- contract
ALTER TABLE ordering.orders
    DROP COLUMN fulfillment_status,
    DROP COLUMN shipped_at,
    DROP COLUMN carrier,
    DROP COLUMN tracking_number,
    DROP COLUMN delivered_at,
    DROP COLUMN hold_reason;
