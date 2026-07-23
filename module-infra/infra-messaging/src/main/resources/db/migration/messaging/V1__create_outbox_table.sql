CREATE SCHEMA IF NOT EXISTS messaging;

CREATE TABLE messaging.outbox (
    id               UUID         NOT NULL,
    event_type       VARCHAR(255) NOT NULL,
    payload          TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    published_at     TIMESTAMPTZ,
    attempt_count    INT          NOT NULL DEFAULT 0,
    dead_lettered_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON messaging.outbox (created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
