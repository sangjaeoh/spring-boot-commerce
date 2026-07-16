-- 리컨실 스윕(findByStatusAndCreatedAtBefore(REQUESTED, cutoff))이 매분 도는 조회의 인덱스.
-- 스윕 대상은 항상 REQUESTED 단일 값이라 부분 인덱스로 좁힌다: 인덱스가 미확정 잔량만큼만 유지되고,
-- 확정된 결제(APPROVED·FAILED·CANCELLED)는 상태 전이 시 인덱스에서 자동 이탈한다 — 결제 누적과 무관하게
-- 인덱스가 작게 남아 크기·쓰기 비용이 최소다.
-- 일반 복합 인덱스((status, created_at), 부분 아님)는 전 행을 색인해 파라미터 바인딩(status = $1)의 제네릭
-- 플랜에서도 확정적으로 쓰이지만, 확정 결제까지 무한 누적돼 커버리지가 필요 없는 곳에 크기·쓰기 비용을 문다.
-- 스윕 유일 쿼리가 REQUESTED 고정이라 부분 인덱스의 커버리지 손실이 없어 부분 인덱스를 택한다(부분 인덱스는
-- status = 'REQUESTED' 리터럴을 아는 커스텀 플랜에서 매칭되며, 대안이 풀스캔이라 플래너가 커스텀 플랜을 유지한다).
CREATE INDEX ix_payment_status_created_at
    ON payment.payment (status, created_at)
    WHERE status = 'REQUESTED';
