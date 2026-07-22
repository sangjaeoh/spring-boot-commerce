package com.commerce.api.web.v1;

import com.commerce.api.SharedPostgresContainer;
import com.commerce.api.SharedRedisContainer;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.product.application.provided.ProductAppender;
import com.commerce.product.application.provided.ProductModifier;
import com.commerce.product.application.provided.ProductVariantAppender;
import com.commerce.product.application.provided.ProductVariantModifier;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockAppender;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public abstract class WebIntegrationTest {

    @Autowired
    private JwtTokenCodec jwtTokenCodec;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductAppender productAppender;

    @Autowired
    private ProductVariantAppender variantAppender;

    @Autowired
    private StockAppender stockAppender;

    @Autowired
    private ProductVariantModifier variantModifier;

    @Autowired
    private ProductModifier productModifier;

    // 공유 Redis에 남은 로그인 레이트리밋 카운터가 테스트 간 새지 않게 각 테스트 전에 비운다.
    @BeforeEach
    void resetLoginRateLimit() {
        Set<String> keys = redisTemplate.keys("login-rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.data.redis.host", SharedRedisContainer.INSTANCE::getRedisHost);
        registry.add("spring.data.redis.port", SharedRedisContainer.INSTANCE::getRedisPort);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
    }

    /** 회원을 주체로 실은 구매자 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String bearer(UUID memberId) {
        return "Bearer " + jwtTokenCodec.issue(memberId.toString(), Map.of("role", "BUYER"));
    }

    /**
     * 상품·첫 변형·초기 재고를 시딩하고 판매를 시작해 상품 ID를 반환한다 — 어드민 앱으로 이전한 상품 등록
     * 경로를 도메인 서비스 직접 호출로 인라인한 픽스처다.
     */
    protected UUID seedOnSaleProduct(String name, @Nullable String description, Money price, int initialQuantity) {
        return seedOnSaleProduct(name, description, null, price, initialQuantity);
    }

    /** 카테고리를 지정해 상품·첫 변형·초기 재고를 시딩하고 판매를 시작해 상품 ID를 반환한다. */
    protected UUID seedOnSaleProduct(
            String name, @Nullable String description, @Nullable UUID categoryId, Money price, int initialQuantity) {
        UUID productId = productAppender.register(name, description, categoryId);
        UUID variantId = variantAppender.create(productId, price, List.of());
        stockAppender.create(variantId, initialQuantity);
        variantModifier.enable(variantId);
        productModifier.show(productId);
        return productId;
    }
}
