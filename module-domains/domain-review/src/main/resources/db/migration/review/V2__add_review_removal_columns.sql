ALTER TABLE review.review
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN removed_reason VARCHAR(255);
