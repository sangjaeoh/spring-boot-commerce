CREATE TABLE product.category (
    id         UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_category PRIMARY KEY (id)
);

-- 상품의 소속 카테고리(논리 참조, 미분류면 NULL)
ALTER TABLE product.product
    ADD COLUMN category_id UUID;

-- 논리 FK 인덱스(물리 FK 없음) — 카테고리 필터 조회 축
CREATE INDEX ix_product_category_id ON product.product (category_id);
