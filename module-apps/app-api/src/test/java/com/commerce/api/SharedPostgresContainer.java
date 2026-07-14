package com.commerce.api;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * app-api 통합 테스트가 공유하는 단일 PostgreSQL 컨테이너 홀더다.
 *
 * <p>JVM 수명 동안 컨테이너 하나만 띄우고 전 스키마 마이그레이션을 정확히 1회 실행한다. 파사드 하네스와 웹
 * 하네스가 모두 이 홀더를 참조해 하네스마다 컨테이너를 띄우고 마이그레이션하던 것을 하나로 합친다. 컨테이너는
 * 명시적으로 멈추지 않고 JVM 종료 시 Testcontainers가 정리한다.
 */
public final class SharedPostgresContainer {

    public static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        INSTANCE.start();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(INSTANCE.getJdbcUrl(), INSTANCE.getUsername(), INSTANCE.getPassword());
        SchemaFlywayFactory.migrateAll(dataSource);
    }

    private SharedPostgresContainer() {}
}
