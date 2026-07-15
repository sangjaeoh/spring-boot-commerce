package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.MemberRegistrationRequest;
import com.commerce.api.presentation.v1.response.MemberRegistrationResponse;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
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
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberModifier memberModifier;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final CheckoutFacade checkoutFacade;

    MemberControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            MemberReader memberReader,
            MemberModifier memberModifier,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            CheckoutFacade checkoutFacade) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.checkoutFacade = checkoutFacade;
    }

    @Test
    @DisplayName("가입이 201로 회원 ID를 반환하고 ACTIVE 회원을 만든다")
    void registerReturns201AndCreatesActiveMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MemberRegistrationRequest request = new MemberRegistrationRequest(email, "테스터");

        String body = mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID memberId = UUID.fromString(
                objectMapper.readValue(body, MemberRegistrationResponse.class).memberId());
        MemberInfo member = memberReader.getMember(memberId);
        assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.email()).isEqualTo(email);
        assertThat(member.name()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("같은 이메일 재가입은 409 MEMBER_DUPLICATE_EMAIL로 거부된다")
    void registerRejectsDuplicateEmail() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String json = objectMapper.writeValueAsString(new MemberRegistrationRequest(email, "테스터"));

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("빈 이름 가입은 400 VALIDATION_FAILED로 거부된다")
    void registerRejectsBlankName() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MemberRegistrationRequest request = new MemberRegistrationRequest(email, "  ");

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("형식이 잘못된 이메일 가입은 400 MEMBER_INVALID_EMAIL_FORMAT로 거부된다")
    void registerRejectsMalformedEmail() throws Exception {
        MemberRegistrationRequest request = new MemberRegistrationRequest("not-an-email", "테스터");

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_EMAIL_FORMAT"));
    }

    @Test
    @DisplayName("회원 상세 조회는 200으로 ACTIVE 상태·이메일·이름을 싣는다")
    void getMemberReturnsActiveMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터");

        mvc.perform(get("/api/v1/members/{memberId}", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("테스터"));
    }

    @Test
    @DisplayName("정지 회원 상세는 SUSPENDED 상태·정지 사유를 싣는다")
    void getMemberIncludesSuspension() throws Exception {
        UUID memberId = registerMember();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        mvc.perform(get("/api/v1/members/{memberId}", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("없는 회원 상세 조회는 404 MEMBER_NOT_FOUND로 거부된다")
    void getMemberReturns404ForMissingMember() throws Exception {
        mvc.perform(get("/api/v1/members/{memberId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
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
    @DisplayName("결제 주문이 배송 완료되면 탈퇴가 204로 성공한다")
    void withdrawSucceedsAfterDelivery() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/orders/{orderId}/delivery-confirmation", orderId))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/members/{memberId}", memberId).param("reason", "NO_LONGER_USED"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
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
