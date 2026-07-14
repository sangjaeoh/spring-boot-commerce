package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberReader;
import com.commerce.order.entity.Address;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final CheckoutFacade checkoutFacade;

    MemberControllerTest(
            MockMvc mvc,
            MemberAppender memberAppender,
            MemberReader memberReader,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            CheckoutFacade checkoutFacade) {
        this.mvc = mvc;
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.checkoutFacade = checkoutFacade;
    }

    @Test
    @DisplayName("탈퇴가 204로 성공하고 회원을 논리삭제한다")
    void withdrawSoftDeletesMember() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/{memberId}", memberId).param("reason", "NO_LONGER_USED"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("미배송 결제 주문이 있으면 탈퇴가 409 API_WITHDRAWAL_BLOCKED로 거부된다")
    void withdrawBlockedByUndeliveredPaidOrder() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        mvc.perform(delete("/api/v1/members/{memberId}", memberId).param("reason", "NO_LONGER_USED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_WITHDRAWAL_BLOCKED"));

        assertThat(memberReader.getMember(memberId).status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("탈퇴 사유가 없는 요청은 400 problem+json으로 거부된다")
    void withdrawRejectsMissingReason() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/{memberId}", memberId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
