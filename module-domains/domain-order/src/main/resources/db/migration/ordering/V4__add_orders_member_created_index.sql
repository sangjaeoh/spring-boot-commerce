-- 주문 목록 ID 페이지 쿼리(where member_id = ? order by created_at desc, id desc)의 정렬을 인덱스로 해소한다.
-- 단일 컬럼 ix_orders_member_id는 member_id로 좁힌 뒤 정렬이 filesort로 남았다. 복합 인덱스가 정렬 순서까지
-- 담아 인덱스 스캔 결과가 곧 정렬 결과다(created_at·id를 인덱스 정의와 같은 DESC로 두어 forward 스캔이 정렬을 만족).
CREATE INDEX ix_orders_member_id_created_at_id
    ON ordering.orders (member_id, created_at DESC, id DESC);

-- 기존 단일 컬럼 인덱스를 제거한다. 복합 인덱스의 선두 컬럼(member_id)이 member_id 선행 조회
-- (findByIdAndMemberId·existsByMemberIdAndStatusAndFulfillmentStatusNot·위 페이지 쿼리)를 그대로 대체하므로,
-- 둘을 함께 두면 쓰기 증폭·저장 비용만 남고 조회 이득은 없다.
DROP INDEX ordering.ix_orders_member_id;
