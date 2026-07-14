package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockReader;
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
    private final StockReader stockReader;

    ProductRegistrationFacadeTest(
            ProductRegistrationFacade productRegistrationFacade,
            ProductReader productReader,
            ProductVariantReader variantReader,
            StockReader stockReader) {
        this.productRegistrationFacade = productRegistrationFacade;
        this.productReader = productReader;
        this.variantReader = variantReader;
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
}
