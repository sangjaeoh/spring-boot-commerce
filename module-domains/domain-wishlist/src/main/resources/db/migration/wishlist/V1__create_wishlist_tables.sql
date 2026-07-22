CREATE SCHEMA IF NOT EXISTS wishlist;

CREATE TABLE wishlist.wishlist_item (
    id         UUID        NOT NULL,
    member_id  UUID        NOT NULL,
    product_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_wishlist_item PRIMARY KEY (id)
);

-- 회원당 상품 찜 유니크(멱등 추가의 최후 방어선). 복합 유니크가 member_id 목록 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_wishlist_item_member_product ON wishlist.wishlist_item (member_id, product_id);
