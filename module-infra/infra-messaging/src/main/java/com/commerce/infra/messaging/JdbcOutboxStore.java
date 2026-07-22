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
                "SELECT id, event_type, payload FROM messaging.outbox WHERE published_at IS NULL"
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
}
