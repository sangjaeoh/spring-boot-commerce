package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.AddressRequest;
import com.commerce.api.presentation.v1.request.CheckoutRequest;
import com.commerce.api.presentation.v1.request.StockIncreaseRequest;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.member.service.MemberAppender;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.service.StockReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StockControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final StockReader stockReader;

    StockControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            StockReader stockReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.stockReader = stockReader;
    }

    @Test
    @DisplayName("재입고가 204로 성공하고 수량을 더한다")
    void increaseAddsQuantity() throws Exception {
        UUID variantId = seedProduct(10);

        increase(variantId, 5).andExpect(status().isNoContent());

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("수량 0 재입고 요청은 400 VALIDATION_FAILED로 거부되고 수량을 바꾸지 않는다")
    void increaseRejectsNonPositiveQuantity() throws Exception {
        UUID variantId = seedProduct(10);

        mvc.perform(post("/api/v1/stocks/{variantId}/increase", variantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("없는 변형 재입고는 404 STOCK_NOT_FOUND로 거부된다")
    void increaseReturns404ForMissingStock() throws Exception {
        increase(UUID.randomUUID(), 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    @DisplayName("단종 재고 재입고는 409 STOCK_CANNOT_INCREASE_DISCONTINUED로 거부된다")
    void increaseRejectsDiscontinuedStock() throws Exception {
        UUID variantId = seedProduct(10);
        mvc.perform(post("/api/v1/stocks/{variantId}/discontinue", variantId)).andExpect(status().isNoContent());
        assertThat(stockReader.getByVariantId(variantId).status()).isEqualTo(StockStatus.DISCONTINUED);

        increase(variantId, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_CANNOT_INCREASE_DISCONTINUED"));

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("수동 품절·재개 왕복이 각각 204로 상태를 전환한다")
    void markSoldOutAndSellableRoundTrip() throws Exception {
        UUID variantId = seedProduct(10);

        mvc.perform(post("/api/v1/stocks/{variantId}/mark-sold-out", variantId)).andExpect(status().isNoContent());
        assertThat(stockReader.getByVariantId(variantId).status()).isEqualTo(StockStatus.SOLD_OUT);

        mvc.perform(post("/api/v1/stocks/{variantId}/mark-sellable", variantId)).andExpect(status().isNoContent());
        assertThat(stockReader.getByVariantId(variantId).status()).isEqualTo(StockStatus.SELLABLE);
    }

    @Test
    @DisplayName("판매 가능 재고의 재개 요청은 409 STOCK_INVALID_STATE_TRANSITION으로 거부된다")
    void markSellableRejectsSellableStock() throws Exception {
        UUID variantId = seedProduct(10);

        mvc.perform(post("/api/v1/stocks/{variantId}/mark-sellable", variantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("단종 재고의 품절 전환은 409 STOCK_INVALID_STATE_TRANSITION으로 거부된다")
    void markSoldOutRejectsDiscontinuedStock() throws Exception {
        UUID variantId = seedProduct(10);
        mvc.perform(post("/api/v1/stocks/{variantId}/discontinue", variantId)).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/stocks/{variantId}/mark-sold-out", variantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("체크아웃으로 소진된 변형이 재입고 후 다시 체크아웃된다")
    void increaseReopensCheckoutAfterExhaustion() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(2);
        cartAppender.addItem(memberId, variantId, 2);
        checkout(memberId).andExpect(status().isCreated());
        assertThat(stockReader.getByVariantId(variantId).quantity()).isZero();

        cartAppender.addItem(memberId, variantId, 2);
        checkout(memberId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_INSUFFICIENT_STOCK"));

        increase(variantId, 2).andExpect(status().isNoContent());

        checkout(memberId).andExpect(status().isCreated());
        assertThat(stockReader.getByVariantId(variantId).quantity()).isZero();
    }

    private ResultActions increase(UUID variantId, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/stocks/{variantId}/increase", variantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StockIncreaseRequest(quantity))));
    }

    private ResultActions checkout(UUID memberId) throws Exception {
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);
        return mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static AddressRequest addressRequest() {
        return new AddressRequest("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
