CREATE TABLE product.product_image (
    id          UUID         NOT NULL,
    product_id  UUID         NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    url         VARCHAR(500) NOT NULL,
    sort_order  INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (id)
);

-- 논리 FK 인덱스(물리 FK 없음)
CREATE INDEX ix_product_image_product_id ON product.product_image (product_id);
