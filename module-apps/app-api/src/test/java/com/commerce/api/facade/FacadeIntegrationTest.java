package com.commerce.api.facade;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 파사드 크로스 도메인 통합 테스트의 공통 하네스다.
 *
 * <p>공유 PostgreSQL 컨테이너를 정적으로 띄우고 전 스키마를 마이그레이션한 뒤 앱을 {@code ddl-auto=validate}로
 * 부팅한다(컨테이너·마이그레이션이 컨텍스트 생성보다 먼저다). 트랜잭션 롤백을 걸지 않으므로(각 도메인 서비스가
 * 자기 트랜잭션을 커밋하고 커밋 후 리스너가 실행) 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class FacadeIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SchemaFlywayFactory.migrateAll(dataSource);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
