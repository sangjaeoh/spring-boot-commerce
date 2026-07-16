-- 관리자 주문 목록(where status = ? and fulfillment_status = ? order by created_at desc, id desc)의 필터·정렬을
-- 인덱스로 해소한다. 출고·환불 대상 발견(결제완료·미출고)이 도는 유일한 관리자 조회 경로다. 두 상태 컬럼을 선두에
-- 두어 필터를 좁히고, created_at·id를 인덱스 정의와 같은 DESC로 담아 인덱스 스캔 결과가 곧 정렬 결과다(forward
-- 스캔이 정렬을 만족해 filesort가 없다). orders는 최대 크기 테이블이라 인덱스 없이는 필터·정렬이 풀스캔이다.
CREATE INDEX ix_orders_status_fulfillment_created_at_id
    ON ordering.orders (status, fulfillment_status, created_at DESC, id DESC);
