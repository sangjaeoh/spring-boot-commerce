-- 재시도 이력(attempt_count)과 dead letter 격리(dead_lettered_at) 컬럼. 격리 행은 재전달에서 제외된다.
ALTER TABLE messaging.outbox ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE messaging.outbox ADD COLUMN dead_lettered_at TIMESTAMPTZ;

-- 폴링 부분 인덱스를 격리 제외 조건으로 재구성한다 — 폴링 비용이 재전달 대상 잔량에만 비례한다.
DROP INDEX messaging.idx_outbox_unpublished;
CREATE INDEX idx_outbox_unpublished ON messaging.outbox (created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
