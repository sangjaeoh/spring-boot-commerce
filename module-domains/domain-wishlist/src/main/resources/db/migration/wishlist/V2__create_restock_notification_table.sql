CREATE TABLE wishlist.restock_notification (
    id         UUID        NOT NULL,
    event_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_restock_notification PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_restock_notification_event ON wishlist.restock_notification (event_id);

CREATE INDEX ix_wishlist_item_product_id ON wishlist.wishlist_item (product_id);
