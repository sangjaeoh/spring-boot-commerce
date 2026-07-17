-- 취소 개시 마커. 취소 파사드가 PG 환불 앞에 커밋하고, 마커가 있는 동안 출고(ship)를 거부한다
-- (환불 커밋과 주문 취소 전이 사이에 출고가 끼어들어 남는 환불 고아를 원천 차단).
ALTER TABLE ordering.orders
    ADD COLUMN cancel_requested_at TIMESTAMPTZ;
