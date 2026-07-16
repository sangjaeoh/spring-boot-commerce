-- 관리자 무효화(REVOKED) 기록. status == REVOKED ⇔ revoked_at·revoke_reason 세팅.
ALTER TABLE coupon.issued_coupon
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoke_reason VARCHAR(255);
