-- 취소·환불 전이가 재고·쿠폰 복원을 게이트하므로 동시 중복 전이를 낙관락으로 직렬화한다.
ALTER TABLE ordering.orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
