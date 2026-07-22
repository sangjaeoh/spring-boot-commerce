package com.commerce.api.facade;

import com.commerce.api.SharedPostgresContainer;
import com.commerce.api.SharedRedisContainer;
import com.commerce.product.application.provided.ProductAppender;
import com.commerce.product.application.provided.ProductModifier;
import com.commerce.product.application.provided.ProductVariantAppender;
import com.commerce.product.application.provided.ProductVariantModifier;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockAppender;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 파사드 크로스 도메인 통합 테스트의 공통 하네스다.
 *
 * <p>{@link SharedPostgresContainer}의 공유 PostgreSQL 컨테이너에 붙어 앱을 {@code ddl-auto=validate}로
 * 부팅한다(컨테이너·마이그레이션이 컨텍스트 생성보다 먼저다). 멱등 키 저장소 등이 기동 직후부터 Redis를
 * 쓰므로 {@link SharedRedisContainer}도 배선한다. 트랜잭션 롤백을 걸지 않으므로(각 도메인 서비스가 자기
 * 트랜잭션을 커밋하고 커밋 후 리스너가 실행) 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class FacadeIntegrationTest {

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

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.data.redis.host", SharedRedisContainer.INSTANCE::getRedisHost);
        registry.add("spring.data.redis.port", SharedRedisContainer.INSTANCE::getRedisPort);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
    }

    /**
     * 상품·첫 변형·초기 재고를 시딩하고 판매를 시작해 상품 ID를 반환한다 — 어드민 앱으로 이전한 상품 등록
     * 경로를 도메인 서비스 직접 호출로 인라인한 픽스처다.
     */
    protected UUID seedOnSaleProduct(String name, @Nullable String description, Money price, int initialQuantity) {
        UUID productId = productAppender.register(name, description, null);
        UUID variantId = variantAppender.create(productId, price, List.of());
        stockAppender.create(variantId, initialQuantity);
        variantModifier.enable(variantId);
        productModifier.show(productId);
        return productId;
    }
}
