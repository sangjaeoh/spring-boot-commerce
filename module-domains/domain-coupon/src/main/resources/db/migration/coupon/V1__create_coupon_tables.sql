CREATE SCHEMA IF NOT EXISTS coupon;

CREATE TABLE coupon.coupon (
    id               UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    discount_type    VARCHAR(10)  NOT NULL,
    discount_amount  BIGINT,
    discount_percent INTEGER,
    discount_max_cap BIGINT,
    min_order_amount BIGINT       NOT NULL,
    valid_from       TIMESTAMPTZ  NOT NULL,
    valid_until      TIMESTAMPTZ  NOT NULL,
    usage_valid_days INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    max_issuance     INTEGER,
    issued_count     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_coupon PRIMARY KEY (id)
);

CREATE TABLE coupon.issued_coupon (
    id            UUID        NOT NULL,
    coupon_id     UUID        NOT NULL,
    member_id     UUID        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    order_id      UUID,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(255),
    CONSTRAINT pk_issued_coupon PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_issued_coupon_coupon_member ON coupon.issued_coupon (coupon_id, member_id);
CREATE INDEX ix_issued_coupon_member_id ON coupon.issued_coupon (member_id);
CREATE INDEX ix_issued_coupon_order_id ON coupon.issued_coupon (order_id);

CREATE INDEX ix_issued_coupon_coupon_id_created_at_id
    ON coupon.issued_coupon (coupon_id, created_at DESC, id DESC);
