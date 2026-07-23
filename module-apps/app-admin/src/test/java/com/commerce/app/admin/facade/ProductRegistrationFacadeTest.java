package com.commerce.app.admin.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.product.application.info.ProductVariantInfo;
import com.commerce.domain.product.application.provided.ProductReader;
import com.commerce.domain.product.application.provided.ProductVariantAppender;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.product.domain.ProductOption;
import com.commerce.domain.product.domain.ProductStatus;
import com.commerce.domain.product.domain.ProductVariantStatus;
import com.commerce.domain.product.domain.exception.DuplicateVariantOptionException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockAppender;
import com.commerce.domain.stock.application.provided.StockReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductRegistrationFacadeTest extends FacadeIntegrationTest {

    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final ProductVariantAppender variantAppender;
    private final StockAppender stockAppender;
    private final StockReader stockReader;

    ProductRegistrationFacadeTest(
            ProductRegistrationFacade productRegistrationFacade,
            ProductReader productReader,
            ProductVariantReader variantReader,
            ProductVariantAppender variantAppender,
            StockAppender stockAppender,
            StockReader stockReader) {
        this.productRegistrationFacade = productRegistrationFacade;
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.variantAppender = variantAppender;
        this.stockAppender = stockAppender;
        this.stockReader = stockReader;
    }

    @Test
    @DisplayName("상품 등록이 ON_SALE 상품·ACTIVE 변형·재고를 시딩한다")
    void registerProductSeedsSellableCatalog() {
        UUID productId = productRegistrationFacade.registerProduct(
                "티셔츠", "면 100%", Money.of(10000L), List.of(new ProductOption("색상", "빨강")), 50);

        assertThat(productReader.getProduct(productId).status()).isEqualTo(ProductStatus.ON_SALE);

        List<ProductVariantInfo> variants = variantReader.getByProductId(productId);
        assertThat(variants).hasSize(1);
        ProductVariantInfo variant = variants.get(0);
        assertThat(variant.status()).isEqualTo(ProductVariantStatus.ACTIVE);
        assertThat(variant.price()).isEqualTo(Money.of(10000L));
        assertThat(variant.optionLabel()).isEqualTo("빨강");
        assertThat(stockReader.getByVariantId(variant.id()).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("추가 변형 등록이 기존 상품에 ACTIVE 변형·재고를 시딩한다")
    void addVariantSeedsSecondSellableVariant() {
        UUID productId = productRegistrationFacade.registerProduct(
                "티셔츠", null, Money.of(10000L), List.of(new ProductOption("색상", "빨강")), 50);

        UUID variantId = productRegistrationFacade.addVariant(
                productId, Money.of(12000L), List.of(new ProductOption("색상", "파랑")), 30);

        assertThat(variantReader.getByProductId(productId)).hasSize(2);
        ProductVariantInfo added = variantReader.getVariant(variantId);
        assertThat(added.status()).isEqualTo(ProductVariantStatus.ACTIVE);
        assertThat(added.price()).isEqualTo(Money.of(12000L));
        assertThat(added.optionLabel()).isEqualTo("파랑");
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("변형만 생성되고 중단된 시딩은 addVariant 재시도가 재고 생성·활성화를 재개한다")
    void addVariantResumesWhenInterruptedBeforeStockSeeding() {
        UUID productId = registerBaseProduct();
        List<ProductOption> options = List.of(new ProductOption("색상", "파랑"));
        // 변형 create 직후(재고 생성 전) 중단을 재현한다.
        UUID interrupted = variantAppender.create(productId, Money.of(12000L), options);

        UUID resumed = productRegistrationFacade.addVariant(productId, Money.of(12000L), options, 30);

        assertThat(resumed).isEqualTo(interrupted);
        assertThat(variantReader.getVariant(interrupted).status()).isEqualTo(ProductVariantStatus.ACTIVE);
        assertThat(stockReader.getByVariantId(interrupted).quantity()).isEqualTo(30);
        assertThat(variantReader.getByProductId(productId)).hasSize(2);
    }

    @Test
    @DisplayName("재고까지 생성되고 미활성으로 중단된 시딩은 재시도가 활성화만 재개하고 재고를 재시딩하지 않는다")
    void addVariantResumesWhenInterruptedBeforeEnable() {
        UUID productId = registerBaseProduct();
        List<ProductOption> options = List.of(new ProductOption("색상", "파랑"));
        // 재고 create까지 끝나고 enable 전 중단을 재현한다.
        UUID interrupted = variantAppender.create(productId, Money.of(12000L), options);
        stockAppender.create(interrupted, 30);

        UUID resumed = productRegistrationFacade.addVariant(productId, Money.of(15000L), options, 99);

        assertThat(resumed).isEqualTo(interrupted);
        assertThat(variantReader.getVariant(interrupted).status()).isEqualTo(ProductVariantStatus.ACTIVE);
        // 재개는 기존 재고를 재시딩하지 않고 기존 가격을 유지한다 — 재시도 입력(99·15000)이 아니라 중단 시점 값.
        assertThat(stockReader.getByVariantId(interrupted).quantity()).isEqualTo(30);
        assertThat(variantReader.getVariant(interrupted).price()).isEqualTo(Money.of(12000L));
    }

    @Test
    @DisplayName("표기가 다른 같은 옵션으로 재시도해도 정규화가 수렴시켜 같은 변형을 재개한다")
    void addVariantResumeConvergesAcrossOptionFormatting() {
        UUID productId = registerBaseProduct();
        UUID interrupted = variantAppender.create(productId, Money.of(12000L), List.of(new ProductOption("색상", "파랑")));

        UUID resumed = productRegistrationFacade.addVariant(
                productId, Money.of(12000L), List.of(new ProductOption(" 색상 ", " 파랑 ")), 30);

        assertThat(resumed).isEqualTo(interrupted);
        assertThat(variantReader.getVariant(interrupted).status()).isEqualTo(ProductVariantStatus.ACTIVE);
        assertThat(stockReader.getByVariantId(interrupted).quantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("완결된 동일 옵션 변형에 대한 addVariant는 여전히 중복으로 거부된다")
    void addVariantStillRejectsCompletedDuplicate() {
        UUID productId = registerBaseProduct();
        List<ProductOption> options = List.of(new ProductOption("색상", "파랑"));
        UUID completed = productRegistrationFacade.addVariant(productId, Money.of(12000L), options, 30);

        assertThatThrownBy(() -> productRegistrationFacade.addVariant(productId, Money.of(15000L), options, 10))
                .isInstanceOf(DuplicateVariantOptionException.class);

        assertThat(variantReader.getVariant(completed).price()).isEqualTo(Money.of(12000L));
        assertThat(stockReader.getByVariantId(completed).quantity()).isEqualTo(30);
    }

    private UUID registerBaseProduct() {
        return productRegistrationFacade.registerProduct(
                "티셔츠", null, Money.of(10000L), List.of(new ProductOption("색상", "빨강")), 50);
    }
}
