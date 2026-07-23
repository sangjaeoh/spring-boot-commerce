CREATE SCHEMA IF NOT EXISTS review;

CREATE TABLE review.review (
    id         UUID          NOT NULL,
    member_id  UUID          NOT NULL,
    product_id UUID          NOT NULL,
    rating     INTEGER       NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_review PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_review_member_product ON review.review (member_id, product_id);
CREATE INDEX ix_review_product_id ON review.review (product_id);
