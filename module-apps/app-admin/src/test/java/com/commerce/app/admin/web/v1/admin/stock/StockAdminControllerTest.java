package com.commerce.app.admin.web.v1.admin.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.admin.facade.ProductRegistrationFacade;
import com.commerce.app.admin.web.v1.WebIntegrationTest;
import com.commerce.app.admin.web.v1.admin.stock.request.StockIncreaseRequest;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockModifier;
import com.commerce.domain.stock.application.provided.StockReader;
import com.commerce.domain.stock.domain.StockStatus;
import com.commerce.domain.stock.domain.exception.StockShortageException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StockAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final StockModifier stockModifier;
    private final StockReader stockReader;

    StockAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            StockModifier stockModifier,
            StockReader stockReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.stockModifier = stockModifier;
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

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/increase", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
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
        mvc.perform(post("/api/v1/admin/stocks/{variantId}/discontinue", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
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

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/mark-sold-out", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(stockReader.getByVariantId(variantId).status()).isEqualTo(StockStatus.SOLD_OUT);

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/mark-sellable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(stockReader.getByVariantId(variantId).status()).isEqualTo(StockStatus.SELLABLE);
    }

    @Test
    @DisplayName("판매 가능 재고의 재개 요청은 409 STOCK_INVALID_STATE_TRANSITION으로 거부된다")
    void markSellableRejectsSellableStock() throws Exception {
        UUID variantId = seedProduct(10);

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/mark-sellable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("단종 재고의 품절 전환은 409 STOCK_INVALID_STATE_TRANSITION으로 거부된다")
    void markSoldOutRejectsDiscontinuedStock() throws Exception {
        UUID variantId = seedProduct(10);
        mvc.perform(post("/api/v1/admin/stocks/{variantId}/discontinue", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/mark-sold-out", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("차감으로 소진된 변형이 재입고 후 다시 차감된다")
    // 체크아웃 표면은 app-api 소유라 소진·재차감은 도메인 차감으로 재현한다. 소진 시 체크아웃 거부는 app-api가 검증한다.
    void increaseReopensDeductionAfterExhaustion() throws Exception {
        UUID variantId = seedProduct(2);
        stockModifier.deduct(variantId, 2);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isZero();

        assertThatThrownBy(() -> stockModifier.deduct(variantId, 2)).isInstanceOf(StockShortageException.class);

        increase(variantId, 2).andExpect(status().isNoContent());

        stockModifier.deduct(variantId, 2);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isZero();
    }

    @Test
    @DisplayName("구매자 토큰의 재입고는 403 FORBIDDEN으로 거부되고 수량을 바꾸지 않는다")
    void increaseRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        UUID variantId = seedProduct(10);

        mvc.perform(post("/api/v1/admin/stocks/{variantId}/increase", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockIncreaseRequest(5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("관리자 재고 현황 조회는 200으로 변형의 수량·상태를 싣고 재고 없는 변형은 생략한다")
    void getStocksReturnsStatusAndOmitsMissing() throws Exception {
        UUID variantId = seedProduct(10);
        UUID missingVariantId = UUID.randomUUID();

        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", variantId.toString(), missingVariantId.toString())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].status").value("SELLABLE"));
    }

    @Test
    @DisplayName("구매자 토큰의 재고 현황 조회는 403 FORBIDDEN으로 거부된다")
    void getStocksRejectsBuyerToken() throws Exception {
        UUID variantId = seedProduct(10);

        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", variantId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 재고 현황 조회는 401 UNAUTHENTICATED로 거부된다")
    void getStocksRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions increase(UUID variantId, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/admin/stocks/{variantId}/increase", variantId)
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StockIncreaseRequest(quantity))));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }
}
