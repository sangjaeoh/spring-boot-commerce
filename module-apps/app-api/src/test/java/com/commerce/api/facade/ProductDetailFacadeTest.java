package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.commerce.api.facade.view.ProductView;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockAppender;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductDetailFacadeTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private StockReader stockReader;

    private final ProductDetailFacade productDetailFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantAppender variantAppender;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;
    private final StockAppender stockAppender;
    private final StockModifier stockModifier;
    private final ProductModifier productModifier;

    ProductDetailFacadeTest(
            ProductDetailFacade productDetailFacade,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantAppender variantAppender,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier,
            StockAppender stockAppender,
            StockModifier stockModifier,
            ProductModifier productModifier) {
        this.productDetailFacade = productDetailFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantAppender = variantAppender;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
        this.stockAppender = stockAppender;
        this.stockModifier = stockModifier;
        this.productModifier = productModifier;
    }

    @Test
    @DisplayName("ACTIVE 변형을 최저가·재고 주문가능과 함께 합성한다(대표가는 품절 변형이라도 최저가)")
    void composesActiveVariantsWithOrderabilityAndFromPrice() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", "면 100%", Money.of(10000L), List.of(), 5);
        // 최저가지만 품절
        addActiveVariant(productId, 8000L, "블랙", 0);
        addActiveVariant(productId, 12000L, "화이트", 3);

        ProductView detail = productDetailFacade.getProductDetail(productId);

        assertThat(detail.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(detail.variants()).hasSize(3);
        assertThat(detail.fromPrice()).isEqualTo(Money.of(8000L));
        assertThat(detail.soldOut()).isFalse();
        assertThat(orderableOf(detail, 8000L)).isFalse();
        assertThat(orderableOf(detail, 10000L)).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 변형이 없으면 대표가 null·품절 아님(제공할 것 없음 ≠ 품절)")
    void nothingToOfferWhenNoActiveVariant() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", null, Money.of(10000L), List.of(), 5);
        variantModifier.disable(firstVariantId(productId));

        ProductView detail = productDetailFacade.getProductDetail(productId);

        assertThat(detail.variants()).isEmpty();
        assertThat(detail.fromPrice()).isNull();
        assertThat(detail.soldOut()).isFalse();
    }

    @Test
    @DisplayName("DISABLED 변형은 상세에서 제외한다")
    void excludesDisabledVariants() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", null, Money.of(10000L), List.of(), 5);
        variantAppender.create(productId, Money.of(5000L), List.of(new ProductOption("색상", "블랙")));

        ProductView detail = productDetailFacade.getProductDetail(productId);

        assertThat(detail.variants()).hasSize(1);
        assertThat(detail.fromPrice()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("수량이 있어도 재고 status가 SELLABLE이 아니면 주문 불가·품절이다")
    void notOrderableWhenStockSoldOutDespiteQuantity() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", null, Money.of(10000L), List.of(), 5);
        stockModifier.markSoldOut(firstVariantId(productId));

        ProductView detail = productDetailFacade.getProductDetail(productId);

        assertThat(detail.variants().get(0).orderable()).isFalse();
        assertThat(detail.soldOut()).isTrue();
    }

    @Test
    @DisplayName("변형 N개의 재고를 배치(IN) 1회로 조회한다(변형별 단건 조회 없음)")
    void queriesStockOnceForAllActiveVariants() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", null, Money.of(10000L), List.of(), 5);
        addActiveVariant(productId, 8000L, "블랙", 3);
        addActiveVariant(productId, 12000L, "화이트", 3);
        clearInvocations(stockReader);

        productDetailFacade.getProductDetail(productId);

        verify(stockReader).getByVariantIds(anyCollection());
        verify(stockReader, never()).getByVariantId(any(UUID.class));
    }

    @Test
    @DisplayName("HIDDEN 상품 상세는 미존재와 같은 404(ProductNotFoundException)로 은닉한다")
    void hidesHiddenProductAsNotFound() {
        UUID productId = productRegistrationFacade.registerProduct("셔츠", null, Money.of(10000L), List.of(), 5);
        productModifier.hide(productId);

        assertThatThrownBy(() -> productDetailFacade.getProductDetail(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private void addActiveVariant(UUID productId, long price, String colorValue, int quantity) {
        UUID variantId =
                variantAppender.create(productId, Money.of(price), List.of(new ProductOption("색상", colorValue)));
        stockAppender.create(variantId, quantity);
        variantModifier.enable(variantId);
    }

    private UUID firstVariantId(UUID productId) {
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static boolean orderableOf(ProductView detail, long price) {
        return detail.variants().stream()
                .filter(variant -> variant.price().equals(Money.of(price)))
                .findFirst()
                .orElseThrow()
                .orderable();
    }
}
