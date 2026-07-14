package com.commerce.api.presentation.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.member.service.MemberAppender;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;

    CartControllerTest(
            MockMvc mvc,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader) {
        this.mvc = mvc;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("장바구니 조회는 200으로 라인별 현재가·소계와 총액을 싣는다")
    void getCartReturnsLinesWithCurrentPriceAndTotal() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);
        cartAppender.addItem(memberId, variantId, 2);

        mvc.perform(get("/api/v1/carts").param("memberId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(10000))
                .andExpect(jsonPath("$.lines[0].subtotal").value(20000))
                .andExpect(jsonPath("$.totalAmount").value(20000));
    }

    @Test
    @DisplayName("장바구니 없는 회원 조회는 200으로 빈 장바구니를 싣는다")
    void getEmptyCartForMemberWithoutCart() throws Exception {
        UUID memberId = UUID.randomUUID();

        mvc.perform(get("/api/v1/carts").param("memberId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID seedVariant(long price) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(price), List.of(), 50);
        return variantReader.getByProductId(productId).get(0).id();
    }
}
