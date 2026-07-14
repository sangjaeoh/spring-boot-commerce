CREATE SCHEMA IF NOT EXISTS cart;

CREATE TABLE cart.cart (
    id         UUID        NOT NULL,
    member_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_cart PRIMARY KEY (id)
);

-- 회원당 장바구니 1개. 유니크 인덱스가 논리 FK 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_cart_member_id ON cart.cart (member_id);

CREATE TABLE cart.cart_item (
    id         UUID        NOT NULL,
    cart_id    UUID        NOT NULL,
    variant_id UUID        NOT NULL,
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_cart_item PRIMARY KEY (id)
);

-- 한 장바구니 내 변형 유니크. 복합 유니크가 cart_id 논리 FK 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_cart_item_cart_variant ON cart.cart_item (cart_id, variant_id);
CREATE INDEX ix_cart_item_variant_id ON cart.cart_item (variant_id);
