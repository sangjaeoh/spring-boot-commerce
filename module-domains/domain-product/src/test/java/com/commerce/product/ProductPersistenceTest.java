package com.commerce.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.product.entity.ProductOption;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.exception.DuplicateVariantOptionException;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductAppender;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * product 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>두 테이블 {@code ddl-auto=validate} 정합, Money 컬럼 왕복, 옵션 조합 부분 유니크(은퇴 후 재등록)를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/product",
            "spring.flyway.schemas=product",
            "spring.flyway.default-schema=product"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ProductAppender.class, ProductVariantAppender.class, ProductVariantModifier.class, ProductVariantReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final ProductAppender productAppender;
    private final ProductVariantAppender variantAppender;
    private final ProductVariantModifier variantModifier;
    private final ProductVariantReader variantReader;
    private final TestEntityManager em;

    ProductPersistenceTest(
            ProductAppender productAppender,
            ProductVariantAppender variantAppender,
            ProductVariantModifier variantModifier,
            ProductVariantReader variantReader,
            TestEntityManager em) {
        this.productAppender = productAppender;
        this.variantAppender = variantAppender;
        this.variantModifier = variantModifier;
        this.variantReader = variantReader;
        this.em = em;
    }

    @Test
    @DisplayName("변형 생성 후 조회 왕복 — Money 컬럼·옵션·validate 스키마 정합")
    void createVariantThenRead() {
        UUID productId = productAppender.register("티셔츠", null);
        em.flush();
        UUID variantId =
                variantAppender.create(productId, Money.of(15000L), List.of(new ProductOption("Color", "Red")));
        em.flush();
        em.clear();

        List<ProductVariantInfo> variants = variantReader.getByProductId(productId);

        assertThat(variants).hasSize(1);
        ProductVariantInfo variant = variants.get(0);
        assertThat(variant.id()).isEqualTo(variantId);
        assertThat(variant.price()).isEqualTo(Money.of(15000L));
        assertThat(variant.optionSignature()).isEqualTo("color:red");
        assertThat(variant.optionLabel()).isEqualTo("Red");
        assertThat(variant.status()).isEqualTo(ProductVariantStatus.DISABLED);
    }

    @Test
    @DisplayName("비-RETIRED 변형과 같은 옵션 조합은 거부된다")
    void duplicateActiveOptionRejected() {
        UUID productId = productAppender.register("티셔츠", null);
        em.flush();
        variantAppender.create(productId, Money.of(15000L), List.of(new ProductOption("Color", "Red")));
        em.flush();

        assertThatThrownBy(() ->
                        variantAppender.create(productId, Money.of(16000L), List.of(new ProductOption("Color", "Red"))))
                .isInstanceOf(DuplicateVariantOptionException.class);
    }

    @Test
    @DisplayName("은퇴 후 같은 옵션 조합으로 재등록은 허용된다")
    void reregisterAfterRetireAllowed() {
        UUID productId = productAppender.register("티셔츠", null);
        em.flush();
        UUID first = variantAppender.create(productId, Money.of(15000L), List.of(new ProductOption("Color", "Red")));
        em.flush();
        variantModifier.retire(first);
        em.flush();
        em.clear();

        UUID second = variantAppender.create(productId, Money.of(16000L), List.of(new ProductOption("Color", "Red")));
        em.flush();

        assertThat(second).isNotEqualTo(first);
    }
}
