package com.commerce.api.presentation.v1;

import com.commerce.api.SharedPostgresContainer;
import com.commerce.api.SharedRedisContainer;
import com.commerce.auth.token.AuthRole;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.service.MemberCredentialValidator;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * 관리자 시딩 속성을 주입하므로 컨텍스트 기동 시 {@link #ADMIN_EMAIL} 관리자 계정이 시딩된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class WebIntegrationTest {

    /** 기동 시딩되는 관리자 계정 자격증명. */
    protected static final String ADMIN_EMAIL = "admin@example.com";

    protected static final String ADMIN_PASSWORD = "admin-password-123!";

    /** PG 웹훅 서명에 쓰는 공유 시크릿. */
    protected static final String WEBHOOK_SECRET = "test-webhook-secret";

    private static @Nullable UUID seededAdminId;

    @Autowired
    private JwtTokenCodec jwtTokenCodec;

    @Autowired
    private MemberCredentialValidator memberCredentialValidator;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.data.redis.host", SharedRedisContainer.INSTANCE::getRedisHost);
        registry.add("spring.data.redis.port", SharedRedisContainer.INSTANCE::getRedisPort);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
        registry.add("auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("payment.webhook.secret", () -> WEBHOOK_SECRET);
    }

    /** 회원을 주체로 실은 구매자 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String bearer(UUID memberId) {
        return "Bearer " + jwtTokenCodec.issue(memberId, AuthRole.BUYER);
    }

    /** 시딩된 관리자를 주체로 실은 관리자 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String adminBearer() {
        UUID adminId = seededAdminId;
        if (adminId == null) {
            adminId = memberCredentialValidator
                    .authenticate(ADMIN_EMAIL, ADMIN_PASSWORD)
                    .id();
            seededAdminId = adminId;
        }
        return "Bearer " + jwtTokenCodec.issue(adminId, AuthRole.ADMIN);
    }
}
