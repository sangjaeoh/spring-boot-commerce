CREATE SCHEMA IF NOT EXISTS stock;

CREATE TABLE stock.stock (
    id         UUID        NOT NULL,
    variant_id UUID        NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_stock PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_stock_variant_id ON stock.stock (variant_id);
