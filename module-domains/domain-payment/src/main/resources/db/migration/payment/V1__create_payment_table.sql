CREATE SCHEMA IF NOT EXISTS payment;

CREATE TABLE payment.payment (
    id                       UUID        NOT NULL,
    order_id                 UUID        NOT NULL,
    amount                   BIGINT      NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    method                   VARCHAR(20),
    failure_reason           VARCHAR(30),
    pg_transaction_id        VARCHAR(255),
    pg_cancel_transaction_id VARCHAR(255),
    approved_at              TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    -- 취소(환불) 전이에 사용자 취소·리컨실·웹훅 확정이 겹칠 수 있어 동시 중복 전이를 낙관락으로 직렬화한다.
    version                  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment PRIMARY KEY (id)
);

-- 주문당 결제 1행. 유니크 인덱스가 order_id 논리 FK 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_payment_order_id ON payment.payment (order_id);

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
