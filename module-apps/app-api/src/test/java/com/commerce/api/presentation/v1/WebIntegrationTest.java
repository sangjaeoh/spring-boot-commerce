package com.commerce.api.presentation.v1;

import com.commerce.api.SharedPostgresContainer;
import com.commerce.auth.token.JwtTokenCodec;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 컨트롤러 웹 통합 테스트의 공통 하네스다.
 *
 * <p>{@link SharedPostgresContainer}의 공유 PostgreSQL 컨테이너에 붙어 앱을 MOCK 웹 환경으로 부팅해 실제
 * 필터 체인·핸들러가 붙은 {@link org.springframework.test.web.servlet.MockMvc}를 제공한다. 트랜잭션 롤백을
 * 걸지 않으므로(각 도메인 서비스가 자기 트랜잭션을 커밋하고 커밋 후 리스너가 실행) 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class WebIntegrationTest {

    @Autowired
    private JwtTokenCodec jwtTokenCodec;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
    }

    /** 회원을 주체로 실은 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String bearer(UUID memberId) {
        return "Bearer " + jwtTokenCodec.issue(memberId);
    }
}
