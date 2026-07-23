package com.commerce.app.api.web.v1.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.api.web.v1.WebIntegrationTest;
import com.commerce.domain.cart.application.provided.CartAppender;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.product.application.provided.ProductVariantModifier;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.shared.entity.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;

    CartControllerTest(
            MockMvc mvc,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier) {
        this.mvc = mvc;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
    }

    @Test
    @DisplayName("장바구니 조회는 토큰 주체의 장바구니를 200으로 라인별 현재가·소계와 총액을 실어 반환한다")
    void getCartReturnsLinesWithCurrentPriceAndTotal() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);
        cartAppender.addItem(memberId, variantId, 2);

        mvc.perform(get("/api/v1/carts").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
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

        mvc.perform(get("/api/v1/carts").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("미인증 장바구니 조회는 401 UNAUTHENTICATED로 거부한다")
    void getCartRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/carts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("담기는 204로 응답하고 토큰 주체의 장바구니에 라인을 반영한다")
    void addItemReturnsNoContentAndReflectsLine() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);

        mvc.perform(post("/api/v1/carts/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(variantId, 2)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/carts").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    @DisplayName("미인증 담기는 401 UNAUTHENTICATED로 거부한다")
    void addItemRejectsUnauthenticated() throws Exception {
        UUID variantId = seedVariant(10000L);

        mvc.perform(post("/api/v1/carts/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(variantId, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("비활성 변형 담기는 409로 거부한다")
    void addItemRejectedForDisabledVariant() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);
        variantModifier.disable(variantId);

        mvc.perform(post("/api/v1/carts/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(variantId, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("수량 0 담기는 400으로 검증 거부한다")
    void addItemRejectedForNonPositiveQuantity() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedVariant(10000L);

        mvc.perform(post("/api/v1/carts/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(variantId, 0)))
                .andExpect(status().isBadRequest());
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedVariant(long price) {
        UUID productId = seedOnSaleProduct("상품", null, Money.of(price), 50);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static String addItemBody(UUID variantId, int quantity) {
        return "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity);
    }
}
