package com.commerce.infra.messaging;

import com.commerce.event.outbox.OutboxMessage;
import com.commerce.event.outbox.OutboxStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 아웃박스 릴레이의 미발행 이벤트 조회·발행 표시를 {@code messaging.outbox}에 위임하는 어댑터다. */
@Component
public final class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcOutboxStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public List<OutboxMessage> fetchUnpublished(int limit) {
        return jdbcTemplate.query(
                "SELECT id, event_type, payload FROM messaging.outbox"
                        + " WHERE published_at IS NULL AND dead_lettered_at IS NULL"
                        + " ORDER BY created_at LIMIT ?",
                (rs, rowNum) -> new OutboxMessage(
                        rs.getObject("id", UUID.class), rs.getString("event_type"), rs.getString("payload")),
                limit);
    }

    @Override
    public void markPublished(UUID id) {
        jdbcTemplate.update(
                "UPDATE messaging.outbox SET published_at = ? WHERE id = ? AND published_at IS NULL",
                Timestamp.from(clock.instant()),
                id);
    }

    @Override
    public boolean recordFailure(UUID id, int maxAttempts) {
        // 증가와 격리 판정을 한 문장으로 묶어 동시 기록에도 상한 초과 격리가 정확히 한 번이다.
        Boolean deadLettered = jdbcTemplate.query(
                "UPDATE messaging.outbox SET attempt_count = attempt_count + 1,"
                        + " dead_lettered_at = CASE WHEN attempt_count + 1 >= ? THEN ? ELSE dead_lettered_at END"
                        + " WHERE id = ? RETURNING dead_lettered_at IS NOT NULL AS dead_lettered",
                rs -> rs.next() && rs.getBoolean("dead_lettered"),
                maxAttempts,
                Timestamp.from(clock.instant()),
                id);
        return Boolean.TRUE.equals(deadLettered);
    }
}
