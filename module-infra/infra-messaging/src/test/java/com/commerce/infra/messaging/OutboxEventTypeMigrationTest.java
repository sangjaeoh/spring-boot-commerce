package com.commerce.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 아웃박스 {@code event_type}의 FQCN → 논리 타입 키 전환 마이그레이션(V2)을 실 PostgreSQL로 검증하는
 * 테스트다. V1까지만 적용한 스키마에 전환 전 형식의 행을 심고 최신까지 마이그레이션해 전환 결과를 본다.
 */
@Testcontainers
class OutboxEventTypeMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Test
    @DisplayName("기존 FQCN 행은 마이그레이션으로 논리 키로 전환된다")
    void migrationConvertsFqcnRowsToLogicalKeys() {
        DataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        messagingFlyway(dataSource, "1").migrate();
        jdbcTemplate.update(
                "INSERT INTO messaging.outbox (id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                "com.commerce.order.event.OrderPaid",
                "{}",
                Timestamp.from(Instant.now()));

        messagingFlyway(dataSource, "latest").migrate();

        List<String> eventTypes = jdbcTemplate.queryForList("SELECT event_type FROM messaging.outbox", String.class);
        assertThat(eventTypes).containsExactly("order.OrderPaid");
    }

    /** SchemaFlywayFactory와 같은 구성으로 messaging 스키마 Flyway를 대상 버전까지로 만든다. */
    private static Flyway messagingFlyway(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("messaging")
                .defaultSchema("messaging")
                .locations("classpath:db/migration/messaging")
                .target(target)
                .load();
    }
}
