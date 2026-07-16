-- PENDING 주문 스윕(findByStatusAndCreatedAtBefore(PENDING, cutoff))이 주기적으로 도는 조회의 인덱스.
-- 스윕 대상은 항상 PENDING 단일 값이라 부분 인덱스로 좁힌다: PENDING은 체크아웃 진행 중에만 머무는 과도 상태라
-- 인덱스가 진행 중·잔존 주문만큼만 유지되고, 종결된 주문(PAID·CANCELLED·REFUNDED)은 상태 전이 시 인덱스에서
-- 자동 이탈한다 — 주문 누적과 무관하게 인덱스가 작게 남아 크기·쓰기 비용이 최소다(payment REQUESTED 부분
-- 인덱스와 같은 근거).
-- 스윕 유일 쿼리가 PENDING 고정이라 부분 인덱스의 커버리지 손실이 없어 부분 인덱스를 택한다(부분 인덱스는
-- status = 'PENDING' 리터럴을 아는 커스텀 플랜에서 매칭되며, 대안이 풀스캔이라 플래너가 커스텀 플랜을 유지한다).
CREATE INDEX ix_orders_status_created_at
    ON ordering.orders (status, created_at)
    WHERE status = 'PENDING';
