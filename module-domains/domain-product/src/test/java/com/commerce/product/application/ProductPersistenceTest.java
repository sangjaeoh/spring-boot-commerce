package com.commerce.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.product.application.info.ProductImageInfo;
import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.application.provided.ProductAppender;
import com.commerce.product.application.provided.ProductImageReader;
import com.commerce.product.application.provided.ProductVariantAppender;
import com.commerce.product.application.provided.ProductVariantModifier;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.application.required.ProductImageRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.application.required.ProductVariantRepository;
import com.commerce.product.domain.Category;
import com.commerce.product.domain.DuplicateVariantOptionException;
import com.commerce.product.domain.NormalizedOptions;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductImage;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.ProductVariantStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@Import({
    DefaultProductAppender.class,
    DefaultProductVariantAppender.class,
    DefaultProductVariantModifier.class,
    DefaultProductVariantReader.class,
    DefaultProductImageReader.class
})
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
    private final ProductImageReader imageReader;
    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final TestEntityManager em;

    ProductPersistenceTest(
            ProductAppender productAppender,
            ProductVariantAppender variantAppender,
            ProductVariantModifier variantModifier,
            ProductVariantReader variantReader,
            ProductImageReader imageReader,
            ProductImageRepository imageRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository,
            TestEntityManager em) {
        this.productAppender = productAppender;
        this.variantAppender = variantAppender;
        this.variantModifier = variantModifier;
        this.variantReader = variantReader;
        this.imageReader = imageReader;
        this.imageRepository = imageRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.em = em;
    }

    @Test
    @DisplayName("변형 생성 후 조회 왕복 — Money 컬럼·옵션·validate 스키마 정합")
    void createVariantThenRead() {
        UUID productId = productAppender.register("티셔츠", null, null);
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
        UUID productId = productAppender.register("티셔츠", null, null);
        em.flush();
        variantAppender.create(productId, Money.of(15000L), List.of(new ProductOption("Color", "Red")));
        em.flush();

        assertThatThrownBy(() ->
                        variantAppender.create(productId, Money.of(16000L), List.of(new ProductOption("Color", "Red"))))
                .isInstanceOf(DuplicateVariantOptionException.class);
    }

    @Test
    @DisplayName("카테고리 필터가 키워드·가격 정렬과 조합되고 null 카테고리는 전체다 — validate 스키마 정합")
    void exposedPageFiltersByCategory() {
        UUID topsId = categoryRepository.save(Category.create("상의")).getId();
        UUID bottomsId = categoryRepository.save(Category.create("하의")).getId();
        UUID cheapShirtId = seedExposedProduct("필터셔츠 저가", topsId, 10000L);
        UUID pricyShirtId = seedExposedProduct("필터셔츠 고가", topsId, 20000L);
        seedExposedProduct("필터바지", bottomsId, 15000L);
        em.flush();
        em.clear();

        // 같은 밀리초에 생성된 UUIDv7은 난수 꼬리 순서라 최신순 상호 순서는 단언하지 않는다.
        Page<Product> tops = productRepository.findExposedPage(
                ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, null, topsId, PageRequest.of(0, 10));
        assertThat(tops.getContent()).extracting(Product::getId).containsExactlyInAnyOrder(pricyShirtId, cheapShirtId);

        Page<Product> topsByKeyword = productRepository.findExposedPage(
                ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, "저가", topsId, PageRequest.of(0, 10));
        assertThat(topsByKeyword.getContent()).extracting(Product::getId).containsExactly(cheapShirtId);

        Page<Product> topsByPriceAsc = productRepository.findExposedPageOrderByPriceAsc(
                ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, null, topsId, PageRequest.of(0, 10));
        assertThat(topsByPriceAsc.getContent()).extracting(Product::getId).containsExactly(cheapShirtId, pricyShirtId);

        Page<Product> all = productRepository.findExposedPage(
                ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, "필터", null, PageRequest.of(0, 10));
        assertThat(all.getTotalElements()).isEqualTo(3);
    }

    /** 카테고리가 지정된 노출 상품(ON_SALE + ACTIVE 변형)을 시딩한다. */
    private UUID seedExposedProduct(String name, UUID categoryId, long price) {
        Product product = Product.create(name, null);
        product.assignCategory(categoryId);
        product.show();
        productRepository.save(product);
        ProductVariant variant =
                ProductVariant.create(product.getId(), Money.of(price), NormalizedOptions.of(List.of()));
        variant.enable();
        variantRepository.save(variant);
        return product.getId();
    }

    @Test
    @DisplayName("이미지 영속 후 상품별 조회는 정렬 순서 오름차순이다 — validate 스키마 정합")
    void productImagesReadInSortOrder() {
        UUID productId = productAppender.register("티셔츠", null, null);
        ProductImage second = ProductImage.create(productId, "k2", "/files/k2", 1);
        ProductImage first = ProductImage.create(productId, "k1", "/files/k1", 0);
        imageRepository.save(second);
        imageRepository.save(first);
        em.flush();
        em.clear();

        List<ProductImageInfo> images = imageReader.getByProductId(productId);

        assertThat(images).hasSize(2);
        assertThat(images.get(0).id()).isEqualTo(first.getId());
        assertThat(images.get(0).url()).isEqualTo("/files/k1");
        assertThat(images.get(0).sortOrder()).isEqualTo(0);
        assertThat(images.get(1).id()).isEqualTo(second.getId());
        assertThat(imageReader.getByProductId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("은퇴 후 같은 옵션 조합으로 재등록은 허용된다")
    void reregisterAfterRetireAllowed() {
        UUID productId = productAppender.register("티셔츠", null, null);
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
