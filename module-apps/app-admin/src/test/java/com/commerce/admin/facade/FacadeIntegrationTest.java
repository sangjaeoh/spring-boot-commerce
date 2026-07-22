package com.commerce.admin.facade;

import com.commerce.admin.SharedPostgresContainer;
import com.commerce.admin.SharedRedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 어드민 파사드 크로스 도메인 통합 테스트의 공통 하네스다.
 *
 * <p>{@link SharedPostgresContainer}의 공유 PostgreSQL 컨테이너에 붙어 앱을 {@code ddl-auto=validate}로
 * 부팅한다(컨테이너·마이그레이션이 컨텍스트 생성보다 먼저다). 트랜잭션 롤백을 걸지 않으므로(각 도메인
 * 서비스가 자기 트랜잭션을 커밋) 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class FacadeIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.data.redis.host", SharedRedisContainer.INSTANCE::getRedisHost);
        registry.add("spring.data.redis.port", SharedRedisContainer.INSTANCE::getRedisPort);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
    }
}
