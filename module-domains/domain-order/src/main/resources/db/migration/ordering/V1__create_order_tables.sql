CREATE SCHEMA IF NOT EXISTS ordering;

CREATE TABLE ordering.orders (
    id                  UUID         NOT NULL,
    order_number        VARCHAR(40)  NOT NULL,
    member_id           UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    fulfillment_status  VARCHAR(20)  NOT NULL,
    total_amount        BIGINT       NOT NULL,
    discount_amount     BIGINT       NOT NULL,
    shipping_fee        BIGINT       NOT NULL,
    pay_amount          BIGINT       NOT NULL,
    issued_coupon_id    UUID,
    recipient_name      VARCHAR(100) NOT NULL,
    zip_code            VARCHAR(20)  NOT NULL,
    road_address        VARCHAR(255) NOT NULL,
    detail_address      VARCHAR(255),
    phone               VARCHAR(30)  NOT NULL,
    shipped_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(30),
    hold_reason         VARCHAR(30),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    refunded_at         TIMESTAMPTZ,
    refund_reason       VARCHAR(30),
    carrier             VARCHAR(100),
    tracking_number     VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0,
    stock_deducted_at   TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    return_status       VARCHAR(30),
    return_requested_at TIMESTAMPTZ,
    return_reason       VARCHAR(30),
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_orders_order_number ON ordering.orders (order_number);
CREATE INDEX ix_orders_issued_coupon_id ON ordering.orders (issued_coupon_id);

CREATE INDEX ix_orders_member_id_created_at_id
    ON ordering.orders (member_id, created_at DESC, id DESC);

CREATE INDEX ix_orders_status_fulfillment_created_at_id
    ON ordering.orders (status, fulfillment_status, created_at DESC, id DESC);

CREATE INDEX ix_orders_status_created_at
    ON ordering.orders (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE ordering.order_line (
    id           UUID         NOT NULL,
    order_id     UUID         NOT NULL,
    variant_id   UUID         NOT NULL,
    product_id   UUID         NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    option_label VARCHAR(255),
    unit_price   BIGINT       NOT NULL,
    quantity     INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_order_line PRIMARY KEY (id)
);

CREATE INDEX ix_order_line_order_id ON ordering.order_line (order_id);
CREATE INDEX ix_order_line_variant_id ON ordering.order_line (variant_id);
CREATE INDEX ix_order_line_product_id ON ordering.order_line (product_id);
