package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.api.facade.view.ProductSummaryView;
import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductRemover;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockAppender;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductCatalogFacadeTest extends FacadeIntegrationTest {

    private final ProductCatalogFacade productCatalogFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantAppender variantAppender;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;
    private final StockAppender stockAppender;
    private final ProductModifier productModifier;
    private final ProductRemover productRemover;

    ProductCatalogFacadeTest(
            ProductCatalogFacade productCatalogFacade,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantAppender variantAppender,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier,
            StockAppender stockAppender,
            ProductModifier productModifier,
            ProductRemover productRemover) {
        this.productCatalogFacade = productCatalogFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantAppender = variantAppender;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
        this.stockAppender = stockAppender;
        this.productModifier = productModifier;
        this.productRemover = productRemover;
    }

    @Test
    @DisplayName("숨김·삭제·ACTIVE 변형 0개 상품은 목록에서 빠지고 노출 상품만 실린다")
    void excludesHiddenDeletedAndNoActiveVariantProducts() {
        UUID exposed = registerExposed("노출", 10000L, 5);
        UUID hidden = registerExposed("숨김", 10000L, 5);
        productModifier.hide(hidden);
        UUID deleted = registerExposed("삭제", 10000L, 5);
        productRemover.delete(deleted);
        UUID noActive = registerExposed("변형없음", 10000L, 5);
        variantModifier.disable(firstVariantId(noActive));

        Page<ProductSummaryView> page = productCatalogFacade.getCatalogPage(PageRequest.of(0, 20));

        List<UUID> ids = page.getContent().stream().map(ProductSummaryView::id).toList();
        assertThat(ids).contains(exposed).doesNotContain(hidden, deleted, noActive);
    }

    @Test
    @DisplayName("대표가는 품절 변형이라도 ACTIVE 변형 최저가고, 주문가능 변형이 남으면 품절이 아니다")
    void derivesFromPriceAcrossActiveVariants() {
        UUID productId = registerExposed("셔츠", 10000L, 5);
        addActiveVariant(productId, 8000L, "블랙", 0); // 최저가지만 품절
        addActiveVariant(productId, 12000L, "화이트", 3);

        ProductSummaryView summary = findSummary(productId);

        assertThat(summary.fromPrice()).isEqualTo(Money.of(8000L));
        assertThat(summary.soldOut()).isFalse();
    }

    @Test
    @DisplayName("DISABLED 변형은 대표가 파생에서 제외한다")
    void excludesDisabledVariantFromFromPrice() {
        UUID productId = registerExposed("셔츠", 10000L, 5);
        variantAppender.create(productId, Money.of(5000L), List.of(new ProductOption("색상", "블랙")));

        ProductSummaryView summary = findSummary(productId);

        assertThat(summary.fromPrice()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("ACTIVE 변형이 모두 소진이면 품절로 표시하되 목록에 남는다")
    void marksSoldOutButKeepsListed() {
        UUID productId = registerExposed("품절셔츠", 10000L, 0);

        ProductSummaryView summary = findSummary(productId);

        assertThat(summary.soldOut()).isTrue();
        assertThat(summary.fromPrice()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("페이지네이션은 최신 등록순으로 겹침 없이 나뉘고 범위 밖 페이지는 빈 페이지다")
    void paginatesNewestFirstWithoutOverlap() {
        UUID first = registerExposed("첫째", 10000L, 5);
        UUID second = registerExposed("둘째", 10000L, 5);
        UUID third = registerExposed("셋째", 10000L, 5);
        List<UUID> newestFirst = Stream.of(first, second, third)
                .sorted(Comparator.<UUID>naturalOrder().reversed())
                .toList();

        Page<ProductSummaryView> page0 = productCatalogFacade.getCatalogPage(PageRequest.of(0, 2));
        Page<ProductSummaryView> page1 = productCatalogFacade.getCatalogPage(PageRequest.of(1, 2));

        assertThat(page0.getContent())
                .extracting(ProductSummaryView::id)
                .containsExactly(newestFirst.get(0), newestFirst.get(1));
        assertThat(page1.getContent().get(0).id()).isEqualTo(newestFirst.get(2));
        assertThat(page0.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(productCatalogFacade
                        .getCatalogPage(PageRequest.of(1_000_000, 2))
                        .getContent())
                .isEmpty();
    }

    private UUID registerExposed(String name, long price, int initialQuantity) {
        return productRegistrationFacade.registerProduct(name, null, Money.of(price), List.of(), initialQuantity);
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

    private ProductSummaryView findSummary(UUID productId) {
        return productCatalogFacade.getCatalogPage(PageRequest.of(0, 20)).getContent().stream()
                .filter(summary -> summary.id().equals(productId))
                .findFirst()
                .orElseThrow();
    }
}
