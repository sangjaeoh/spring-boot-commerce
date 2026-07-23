CREATE TABLE ordering.fulfillment (
    id              UUID        NOT NULL,
    order_id        UUID        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    shipped_at      TIMESTAMPTZ,
    carrier         VARCHAR(100),
    tracking_number VARCHAR(100),
    delivered_at    TIMESTAMPTZ,
    hold_reason     VARCHAR(30),
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_fulfillment PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_fulfillment_order_id ON ordering.fulfillment (order_id);

-- 이행이 시작된(NOT_STARTED가 아닌) 기존 주문만 백필한다. PENDING 주문은 이행 행이 없는 것이 정상 상태다.
INSERT INTO ordering.fulfillment
    (id, order_id, status, shipped_at, carrier, tracking_number, delivered_at, hold_reason, version, created_at, updated_at)
SELECT
    gen_random_uuid(), id, fulfillment_status, shipped_at, carrier, tracking_number, delivered_at, hold_reason, 0, created_at, updated_at
FROM ordering.orders
WHERE fulfillment_status <> 'NOT_STARTED';
