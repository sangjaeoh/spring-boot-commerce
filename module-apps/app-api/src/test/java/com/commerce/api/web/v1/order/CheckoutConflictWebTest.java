package com.commerce.api.web.v1.order;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.order.request.AddressRequest;
import com.commerce.api.web.v1.order.request.CheckoutRequest;
import com.commerce.cart.service.CartAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.entity.Stock;
import com.commerce.stock.service.StockModifier;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 재고 낙관락 충돌이 체크아웃 엔드포인트에서 409 CONCURRENT_MODIFICATION으로 매핑되는 웹 회귀다.
 *
 * <p>실경합은 비결정적이라, 차감 지점({@link StockModifier#deduct})을 spy로 {@link
 * ObjectOptimisticLockingFailureException}을 던지게 해 표면 동작(재시도 없이 409)을 결정적으로 고정한다.
 * 매핑 자체는 {@code ProblemDetailHandler}가 소유하고, 여기선 실제 컨트롤러·핸들러 체인을 태워 회귀를 잡는다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutConflictWebTest extends WebIntegrationTest {

    @MockitoSpyBean
    private StockModifier stockModifier;

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;

    CheckoutConflictWebTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("체크아웃 중 재고 낙관락 충돌은 409 CONCURRENT_MODIFICATION으로 매핑된다")
    void checkoutStockConflictMapsToConflict() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        doThrow(new ObjectOptimisticLockingFailureException(Stock.class, UUID.randomUUID()))
                .when(stockModifier)
                .deduct(eq(variantId), anyInt());
        CheckoutRequest request = new CheckoutRequest(addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static AddressRequest addressRequest() {
        return new AddressRequest("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
