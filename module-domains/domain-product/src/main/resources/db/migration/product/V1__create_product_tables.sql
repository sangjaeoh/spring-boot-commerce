CREATE SCHEMA IF NOT EXISTS product;

CREATE TABLE product.product (
    id          UUID          NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(20)   NOT NULL,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id)
);

CREATE TABLE product.product_variant (
    id               UUID          NOT NULL,
    product_id       UUID          NOT NULL,
    price            BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    option_signature VARCHAR(1000) NOT NULL,
    option_label     VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_product_variant PRIMARY KEY (id)
);

-- 논리 FK 인덱스(물리 FK 없음)
CREATE INDEX ix_product_variant_product_id ON product.product_variant (product_id);

-- 비-RETIRED 변형만 (product_id, option_signature) 유니크. 은퇴 조합 재등록을 허용한다.
CREATE UNIQUE INDEX ux_product_variant_active_option
    ON product.product_variant (product_id, option_signature)
    WHERE status <> 'RETIRED';
