package com.commerce.admin;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * app-admin 통합 테스트가 공유하는 단일 PostgreSQL 컨테이너 홀더다.
 *
 * <p>JVM 수명 동안 컨테이너 하나만 띄우고 전 스키마 마이그레이션을 정확히 1회 실행한다. 컨테이너는 명시적으로
 * 멈추지 않고 JVM 종료 시 Testcontainers가 정리한다.
 */
public final class SharedPostgresContainer {

    public static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        // 컨텍스트 캐시의 컨텍스트마다 Hikari 풀(10)이 붙는다. 기본 max_connections=100은 컨텍스트 10개에서
        // 소진되므로 상한을 올려 스위트 성장에 여유를 둔다.
        INSTANCE.setCommand("postgres", "-c", "max_connections=300");
        INSTANCE.start();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(INSTANCE.getJdbcUrl(), INSTANCE.getUsername(), INSTANCE.getPassword());
        SchemaFlywayFactory.migrateAll(dataSource);
    }

    private SharedPostgresContainer() {}
}
