package com.commerce.batch;

import com.commerce.product.service.ProductAppender;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockAppender;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * app-batch 통합 테스트의 공통 하네스다.
 *
 * <p>{@link SharedPostgresContainer}의 공유 PostgreSQL 컨테이너에 붙어 앱을 {@code ddl-auto=validate}로
 * 부팅한다(컨테이너·마이그레이션이 컨텍스트 생성보다 먼저다). 스케줄 스윕의 ShedLock 락 획득이 기동
 * 직후부터 Redis를 쓰므로 {@link SharedRedisContainer}도 배선한다. 트랜잭션 롤백을 걸지 않으므로(각 도메인
 * 서비스가 자기 트랜잭션을 커밋하고 커밋 후 리스너가 실행) 테스트마다 임의 키로 데이터를 격리한다.
 */
@SpringBootTest
public abstract class BatchIntegrationTest {

    /** PG 웹훅 서명에 쓰는 공유 시크릿. */
    protected static final String WEBHOOK_SECRET = "test-webhook-secret";

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
        registry.add("payment.webhook.secret", () -> WEBHOOK_SECRET);
    }

    /**
     * 상품·첫 변형·초기 재고를 시딩하고 판매를 시작해 상품 ID를 반환한다 — app-api
     * ProductRegistrationFacade의 시딩 경로를 도메인 서비스 직접 호출로 인라인한 픽스처다.
     */
    protected UUID seedOnSaleProduct(Money price, int initialQuantity) {
        UUID productId = productAppender.register("상품", null, null);
        UUID variantId = variantAppender.create(productId, price, List.of());
        stockAppender.create(variantId, initialQuantity);
        variantModifier.enable(variantId);
        productModifier.show(productId);
        return productId;
    }
}
