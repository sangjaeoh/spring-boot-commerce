package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.api.facade.view.CartLineView;
import com.commerce.api.facade.view.CartView;
import com.commerce.cart.service.CartAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductRemover;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockModifier;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartViewFacadeTest extends FacadeIntegrationTest {

    private final CartViewFacade cartViewFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;
    private final ProductModifier productModifier;
    private final ProductRemover productRemover;
    private final StockModifier stockModifier;

    CartViewFacadeTest(
            CartViewFacade cartViewFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier,
            ProductModifier productModifier,
            ProductRemover productRemover,
            StockModifier stockModifier) {
        this.cartViewFacade = cartViewFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
        this.productModifier = productModifier;
        this.productRemover = productRemover;
        this.stockModifier = stockModifier;
    }

    @Test
    @DisplayName("라인별 변형 현재가·소계와 총액을 합성한다")
    void composesLinesWithCurrentPriceAndTotal() {
        UUID memberId = registerMember();
        UUID cheaper = seedVariant(10000L);
        UUID pricier = seedVariant(3000L);
        cartAppender.addItem(memberId, cheaper, 2);
        cartAppender.addItem(memberId, pricier, 1);

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(view.lines()).hasSize(2);
        assertThat(view.totalAmount()).isEqualTo(Money.of(23000L));
        assertThat(subtotalOf(view, cheaper)).isEqualTo(Money.of(20000L));
        assertThat(subtotalOf(view, pricier)).isEqualTo(Money.of(3000L));
    }

    @Test
    @DisplayName("변형 가격 변경이 장바구니 뷰의 현재가에 반영된다")
    void reflectsCurrentVariantPrice() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);
        cartAppender.addItem(memberId, variantId, 1);
        variantModifier.changePrice(variantId, Money.of(15000L));

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(view.lines().get(0).unitPrice()).isEqualTo(Money.of(15000L));
        assertThat(view.totalAmount()).isEqualTo(Money.of(15000L));
    }

    @Test
    @DisplayName("비활성(DISABLED) 변형 라인은 unavailable로 표시되고 총액에서 제외된다")
    void disabledVariantLineIsUnavailable() {
        UUID memberId = registerMember();
        UUID normal = seedVariant(10000L);
        UUID disabled = seedVariant(5000L);
        cartAppender.addItem(memberId, normal, 1);
        cartAppender.addItem(memberId, disabled, 2);
        variantModifier.disable(disabled);

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(lineOf(view, normal).orderable()).isTrue();
        assertThat(lineOf(view, disabled).orderable()).isFalse();
        assertThat(lineOf(view, disabled).subtotal()).isEqualTo(Money.of(10000L));
        assertThat(view.totalAmount()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("숨김(HIDDEN) 상품의 변형 라인은 unavailable로 표시되고 총액에서 제외된다")
    void hiddenProductLineIsUnavailable() {
        UUID memberId = registerMember();
        UUID normal = seedVariant(10000L);
        UUID productId = seedProduct(5000L);
        UUID hidden = variantReader.getByProductId(productId).get(0).id();
        cartAppender.addItem(memberId, normal, 1);
        cartAppender.addItem(memberId, hidden, 1);
        productModifier.hide(productId);

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(lineOf(view, hidden).orderable()).isFalse();
        assertThat(view.totalAmount()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("삭제된 상품의 변형 라인은 unavailable로 표시되고 총액에서 제외된다")
    void deletedProductLineIsUnavailable() {
        UUID memberId = registerMember();
        UUID normal = seedVariant(10000L);
        UUID productId = seedProduct(5000L);
        UUID deleted = variantReader.getByProductId(productId).get(0).id();
        cartAppender.addItem(memberId, normal, 1);
        cartAppender.addItem(memberId, deleted, 1);
        productRemover.delete(productId);

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(lineOf(view, deleted).orderable()).isFalse();
        assertThat(view.totalAmount()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("품절(수량 0) 변형 라인은 unavailable로 표시되고 총액에서 제외된다 — 카탈로그 품절 파생과 동일 기준")
    void soldOutLineIsUnavailable() {
        UUID memberId = registerMember();
        UUID normal = seedVariant(10000L);
        UUID soldOut = seedVariant(5000L);
        cartAppender.addItem(memberId, normal, 1);
        cartAppender.addItem(memberId, soldOut, 1);
        stockModifier.deduct(soldOut, 50);

        CartView view = cartViewFacade.getCartView(memberId);

        assertThat(lineOf(view, soldOut).orderable()).isFalse();
        assertThat(view.totalAmount()).isEqualTo(Money.of(10000L));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(long price) {
        return seedOnSaleProduct("상품", null, Money.of(price), 50);
    }

    private UUID seedVariant(long price) {
        return variantReader.getByProductId(seedProduct(price)).get(0).id();
    }

    private static Money subtotalOf(CartView view, UUID variantId) {
        return lineOf(view, variantId).subtotal();
    }

    private static CartLineView lineOf(CartView view, UUID variantId) {
        return view.lines().stream()
                .filter(line -> line.variantId().equals(variantId))
                .findFirst()
                .orElseThrow();
    }
}
