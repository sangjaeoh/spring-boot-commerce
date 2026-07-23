CREATE SCHEMA IF NOT EXISTS wishlist;

CREATE TABLE wishlist.wishlist_item (
    id         UUID        NOT NULL,
    member_id  UUID        NOT NULL,
    product_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_wishlist_item PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_wishlist_item_member_product ON wishlist.wishlist_item (member_id, product_id);
