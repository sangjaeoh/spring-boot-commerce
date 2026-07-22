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

-- 회원당 상품 1리뷰(중복 작성의 최후 방어선). 복합 유니크가 member_id 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_review_member_product ON review.review (member_id, product_id);
-- 상품별 공개 목록 조회 축.
CREATE INDEX ix_review_product_id ON review.review (product_id);
