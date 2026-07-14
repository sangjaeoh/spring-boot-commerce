package com.commerce.api.presentation.v1;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 컨트롤러 웹 통합 테스트의 공통 하네스다.
 *
 * <p>공유 PostgreSQL 컨테이너를 정적으로 띄우고 전 스키마를 마이그레이션한 뒤 앱을 MOCK 웹 환경으로
 * 부팅해 실제 필터 체인·핸들러가 붙은 {@link org.springframework.test.web.servlet.MockMvc}를 제공한다.
 * 트랜잭션 롤백을 걸지 않으므로(각 도메인 서비스가 자기 트랜잭션을 커밋하고 커밋 후 리스너가 실행)
 * 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class WebIntegrationTest {

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
