CREATE SCHEMA IF NOT EXISTS cart;

CREATE TABLE cart.cart (
    id         UUID        NOT NULL,
    member_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_cart PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_cart_member_id ON cart.cart (member_id);

CREATE TABLE cart.cart_item (
    id         UUID        NOT NULL,
    cart_id    UUID        NOT NULL,
    variant_id UUID        NOT NULL,
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_cart_item PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_cart_item_cart_variant ON cart.cart_item (cart_id, variant_id);
CREATE INDEX ix_cart_item_variant_id ON cart.cart_item (variant_id);
