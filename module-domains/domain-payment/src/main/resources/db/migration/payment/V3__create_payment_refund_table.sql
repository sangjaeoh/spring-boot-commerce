CREATE TABLE payment.payment_refund (
    id                       UUID        NOT NULL,
    payment_id               UUID        NOT NULL,
    amount                   BIGINT      NOT NULL,
    pg_cancel_transaction_id VARCHAR(255),
    refund_key               UUID        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_payment_refund PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_payment_refund_refund_key ON payment.payment_refund (refund_key);

CREATE INDEX ix_payment_refund_payment_id ON payment.payment_refund (payment_id);
