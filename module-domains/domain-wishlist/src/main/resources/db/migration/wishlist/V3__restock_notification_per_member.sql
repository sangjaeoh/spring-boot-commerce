ALTER TABLE wishlist.restock_notification
    ADD COLUMN member_id UUID;

DROP INDEX wishlist.ux_restock_notification_event;

CREATE UNIQUE INDEX ux_restock_notification_event_member
    ON wishlist.restock_notification (event_id, member_id);
