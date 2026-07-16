-- 동일 라인 동시 수량 합산(더블서밋)의 유실을 낙관락으로 막는다.
ALTER TABLE cart.cart_item
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
