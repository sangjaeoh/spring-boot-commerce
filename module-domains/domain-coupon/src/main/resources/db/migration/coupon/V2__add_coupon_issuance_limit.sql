-- 발급 한도(선택)와 소진 카운트. 카운트는 원자적 조건부 UPDATE(issued_count < max_issuance)로만 증가한다.
ALTER TABLE coupon.coupon
    ADD COLUMN max_issuance INTEGER,
    ADD COLUMN issued_count INTEGER NOT NULL DEFAULT 0;
