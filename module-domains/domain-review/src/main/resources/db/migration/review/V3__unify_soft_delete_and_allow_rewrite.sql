ALTER TABLE review.review
    ADD COLUMN deleted_by VARCHAR(20);

UPDATE review.review SET deleted_by = 'ADMIN' WHERE deleted_at IS NOT NULL;

DROP INDEX review.ux_review_member_product;

CREATE UNIQUE INDEX ux_review_member_product_active
    ON review.review (member_id, product_id)
    WHERE deleted_at IS NULL;
