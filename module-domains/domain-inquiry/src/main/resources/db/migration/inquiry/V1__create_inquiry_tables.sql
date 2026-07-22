CREATE SCHEMA IF NOT EXISTS inquiry;

CREATE TABLE inquiry.inquiry (
    id         UUID          NOT NULL,
    member_id  UUID          NOT NULL,
    product_id UUID          NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    secret     BOOLEAN       NOT NULL,
    answer     VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_inquiry PRIMARY KEY (id)
);

-- 상품별 공개 목록 조회 축.
CREATE INDEX ix_inquiry_product_id ON inquiry.inquiry (product_id);
