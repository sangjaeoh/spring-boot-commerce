package com.commerce.admin.web.v1.admin.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.facade.ProductRegistrationFacade;
import com.commerce.admin.web.v1.WebIntegrationTest;
import com.commerce.admin.web.v1.admin.product.request.VariantPriceChangeRequest;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderReader;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
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
class ProductVariantAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final OrderReader orderReader;

    ProductVariantAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            OrderReader orderReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.orderReader = orderReader;
    }

    @Test
    @DisplayName("가격 변경이 204로 성공하고 변형 판매가를 바꾼다")
    void changePriceUpdatesVariantPrice() throws Exception {
        UUID variantId = seedVariant(10000L, 10);

        changePrice(variantId, 12000L).andExpect(status().isNoContent());

        assertThat(variantReader.getVariant(variantId).price()).isEqualTo(Money.of(12000L));
    }

    @Test
    @DisplayName("가격 0 변경 요청은 400 VALIDATION_FAILED로 거부되고 가격을 바꾸지 않는다")
    void changePriceRejectsNonPositivePrice() throws Exception {
        UUID variantId = seedVariant(10000L, 10);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/price-change", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(variantReader.getVariant(variantId).price()).isEqualTo(Money.of(10000L));
    }

    @Test
    @DisplayName("없는 변형 가격 변경은 404 PRODUCT_VARIANT_NOT_FOUND로 거부된다")
    void changePriceReturns404ForMissingVariant() throws Exception {
        changePrice(UUID.randomUUID(), 12000L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("비활성·재활성 왕복이 각각 204로 상태를 전환한다")
    void disableAndEnableRoundTrip() throws Exception {
        UUID variantId = seedVariant(10000L, 10);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/disable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(variantReader.getVariant(variantId).status()).isEqualTo(ProductVariantStatus.DISABLED);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/enable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(variantReader.getVariant(variantId).status()).isEqualTo(ProductVariantStatus.ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE 변형의 재활성 요청은 409 PRODUCT_VARIANT_INVALID_STATE_TRANSITION으로 거부된다")
    void enableRejectsActiveVariant() throws Exception {
        UUID variantId = seedVariant(10000L, 10);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/enable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("구매자 토큰의 변형 비활성은 403 FORBIDDEN으로 거부되고 상태를 바꾸지 않는다")
    void disableRejectsBuyerToken() throws Exception {
        UUID buyerId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        UUID variantId = seedVariant(10000L, 10);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/disable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(variantReader.getVariant(variantId).status()).isEqualTo(ProductVariantStatus.ACTIVE);
    }

    @Test
    @DisplayName("은퇴가 204로 성공하고 RETIRED 변형은 가격 변경·재활성이 409로 거부된다")
    void retiredVariantRejectsPriceChangeAndEnable() throws Exception {
        UUID variantId = seedVariant(10000L, 10);

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/retire", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(variantReader.getVariant(variantId).status()).isEqualTo(ProductVariantStatus.RETIRED);

        changePrice(variantId, 12000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_INVALID_STATE_TRANSITION"));
        assertThat(variantReader.getVariant(variantId).price()).isEqualTo(Money.of(10000L));

        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/enable", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("가격 변경은 기존 주문 스냅샷에 영향을 주지 않는다")
    void changePriceDoesNotAffectExistingOrderSnapshot() throws Exception {
        UUID memberId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        UUID variantId = seedVariant(10000L, 10);
        UUID orderId = placePaidOrder(memberId, variantId, 1);

        changePrice(variantId, 20000L).andExpect(status().isNoContent());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.lines().get(0).unitPrice()).isEqualTo(Money.of(10000L));
        assertThat(order.totalAmount()).isEqualTo(Money.of(10000L));
    }

    private ResultActions changePrice(UUID variantId, long price) throws Exception {
        return mvc.perform(post("/api/v1/admin/product-variants/{variantId}/price-change", variantId)
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VariantPriceChangeRequest(price))));
    }

    private UUID seedVariant(long price, int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(price), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }
}
