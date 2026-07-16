-- 취소(환불) 전이에 사용자 취소·리컨실·웹훅 확정이 겹칠 수 있어 동시 중복 전이를 낙관락으로 직렬화한다.
ALTER TABLE payment.payment
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
