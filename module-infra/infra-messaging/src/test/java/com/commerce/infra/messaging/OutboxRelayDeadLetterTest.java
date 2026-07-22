package com.commerce.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.commerce.event.registry.MapEventTypeRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * 아웃박스 릴레이의 실패 재시도·dead letter 격리를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>실패는 미등록 타입 키(해석 실패)로 유발하고, 스케줄 없이 릴레이를 직접 구동해 주기를 결정적으로
 * 재현한다. 단언은 행 단위라 테스트 간 순서에 독립이다.
 */
@Testcontainers
class OutboxRelayDeadLetterTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static JdbcTemplate jdbcTemplate;
    static JdbcOutboxStore outboxStore;
    static OutboxRelay relay;

    @BeforeAll
    static void setUp() {
        DataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("messaging")
                .defaultSchema("messaging")
                .locations("classpath:db/migration/messaging")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        outboxStore = new JdbcOutboxStore(jdbcTemplate, Clock.systemUTC());
        relay = new OutboxRelay(outboxStore, event -> {}, new ObjectMapper(), new MapEventTypeRegistry(Map.of()));
    }

    @Test
    @DisplayName("실패 행은 시도 횟수가 기록되고 다음 주기에 재시도된다")
    void failedRowRecordsAttemptAndIsRetriedNextCycle() {
        UUID rowId = insertUnresolvableRow();

        relay.relay();

        assertThat(attemptCount(rowId)).isEqualTo(1);
        assertThat(deadLetteredAt(rowId)).isNull();

        relay.relay();

        assertThat(attemptCount(rowId)).isEqualTo(2);
    }

    @Test
    @DisplayName("상한 도달 행은 dead letter로 격리되어 이후 재전달에서 제외된다")
    void rowReachingCapIsDeadLetteredAndExcludedFromRedelivery() {
        UUID rowId = insertUnresolvableRow();

        for (int i = 0; i < OutboxRelay.MAX_ATTEMPTS; i++) {
            relay.relay();
        }

        assertThat(attemptCount(rowId)).isEqualTo(OutboxRelay.MAX_ATTEMPTS);
        assertThat(deadLetteredAt(rowId)).isNotNull();

        relay.relay();

        assertThat(attemptCount(rowId)).isEqualTo(OutboxRelay.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("격리 행은 경고 로그로 드러난다")
    void deadLetteredRowIsSurfacedByWarnLog() {
        UUID rowId = insertUnresolvableRow();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger relayLogger = (Logger) LoggerFactory.getLogger(OutboxRelay.class);
        relayLogger.addAppender(appender);

        try {
            for (int i = 0; i < OutboxRelay.MAX_ATTEMPTS; i++) {
                relay.relay();
            }
        } finally {
            relayLogger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getFormattedMessage()).contains("dead letter").contains(rowId.toString());
        });
    }

    /** 어떤 레지스트리에도 등록되지 않은 타입 키의 행을 삽입한다 — 매 주기 해석 실패한다. */
    private static UUID insertUnresolvableRow() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO messaging.outbox (id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                id,
                "test.Unknown",
                "{}",
                Timestamp.from(Instant.now()));
        return id;
    }

    private static int attemptCount(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM messaging.outbox WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private static @Nullable Timestamp deadLetteredAt(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT dead_lettered_at FROM messaging.outbox WHERE id = ?", Timestamp.class, id);
    }
}
