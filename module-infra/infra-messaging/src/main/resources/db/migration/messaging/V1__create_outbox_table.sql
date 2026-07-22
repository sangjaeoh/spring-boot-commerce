CREATE SCHEMA IF NOT EXISTS messaging;

CREATE TABLE messaging.outbox (
    id           UUID        NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

-- 릴레이 폴링(미발행분 생성순 조회) 전용 부분 인덱스. 발행 완료 행은 인덱스에서 빠져 폴링 비용이
-- 미발행 잔량에만 비례한다.
CREATE INDEX idx_outbox_unpublished ON messaging.outbox (created_at) WHERE published_at IS NULL;
