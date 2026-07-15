package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.member.service.MemberAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartViewFacadeTest extends FacadeIntegrationTest {

    private final CartViewFacade cartViewFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;

    CartViewFacadeTest(
            CartViewFacade cartViewFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier) {
        this.cartViewFacade = cartViewFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
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

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedVariant(long price) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(price), List.of(), 50);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Money subtotalOf(CartView view, UUID variantId) {
        return view.lines().stream()
                .filter(line -> line.variantId().equals(variantId))
                .findFirst()
                .orElseThrow()
                .subtotal();
    }
}
