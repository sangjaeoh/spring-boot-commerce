CREATE SCHEMA IF NOT EXISTS payment;

CREATE TABLE payment.payment (
    id                       UUID        NOT NULL,
    order_id                 UUID        NOT NULL,
    amount                   BIGINT      NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    method                   VARCHAR(20),
    failure_reason           VARCHAR(30),
    pg_transaction_id        VARCHAR(255),
    pg_cancel_transaction_id VARCHAR(255),
    approved_at              TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (id)
);

-- 주문당 결제 1행. 유니크 인덱스가 order_id 논리 FK 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_payment_order_id ON payment.payment (order_id);
