package com.commerce.infra.messaging;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.messaging.event.DomainEvent;
import com.commerce.messaging.publish.MessagePublisher;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * 도메인 이벤트를 진행 중 트랜잭션 안에서 아웃박스 행으로 영속하는 transport 어댑터다.
 *
 * <p>발행이 발행 도메인의 커밋과 함께 원자적으로 확정되고, 전달은 아웃박스 릴레이(app-batch)가 커밋된
 * 행만 읽어 수행한다 — 커밋-발행 사이 유실 창이 없다.
 */
@Component
public final class OutboxMessagePublisher implements MessagePublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxMessagePublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        // 트랜잭션 밖 발행은 커밋-발행 원자성 계약 위반이다 — 조용한 자동커밋 대신 기동적으로 거부한다.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "아웃박스 발행은 활성 트랜잭션 안에서만 허용된다: " + event.getClass().getName());
        }
        jdbcTemplate.update(
                "INSERT INTO messaging.outbox (id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                UuidV7Generator.generate(),
                event.getClass().getName(),
                objectMapper.writeValueAsString(event),
                Timestamp.from(clock.instant()));
    }
}
