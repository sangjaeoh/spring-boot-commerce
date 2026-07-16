package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.AddCartItemRequest;
import com.commerce.api.presentation.v1.request.MemberRegistrationRequest;
import com.commerce.api.presentation.v1.request.MemberRenameRequest;
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
import org.springframework.http.HttpHeaders;
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
        MemberRegistrationRequest request = new MemberRegistrationRequest(email, "테스터", "password-123!");

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
        String json = objectMapper.writeValueAsString(new MemberRegistrationRequest(email, "테스터", "password-123!"));

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
        MemberRegistrationRequest request = new MemberRegistrationRequest(email, "  ", "password-123!");

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
        MemberRegistrationRequest request = new MemberRegistrationRequest("not-an-email", "테스터", "password-123!");

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_EMAIL_FORMAT"));
    }

    @Test
    @DisplayName("8자 미만 패스워드 가입은 400 MEMBER_INVALID_PASSWORD_FORMAT로 거부된다")
    void registerRejectsShortPassword() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MemberRegistrationRequest request = new MemberRegistrationRequest(email, "테스터", "a2345!7");

        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_PASSWORD_FORMAT"));
    }

    @Test
    @DisplayName("관리자의 회원 상세 조회는 200으로 ACTIVE 상태·이메일·이름을 싣는다")
    void getMemberReturnsActiveMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        mvc.perform(get("/api/v1/members/{memberId}", memberId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("테스터"));
    }

    @Test
    @DisplayName("본인 조회(/me)는 200으로 토큰 주체의 상세를 싣는다")
    void getMeReturnsTokenSubjectDetail() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        mvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("미인증 본인 조회(/me)는 401 UNAUTHENTICATED로 거부된다")
    void getMeRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("정지 회원 상세는 SUSPENDED 상태·정지 사유를 싣는다")
    void getMemberIncludesSuspension() throws Exception {
        UUID memberId = registerMember();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        mvc.perform(get("/api/v1/members/{memberId}", memberId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("없는 회원 상세 조회는 404 MEMBER_NOT_FOUND로 거부된다")
    void getMemberReturns404ForMissingMember() throws Exception {
        mvc.perform(get("/api/v1/members/{memberId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("구매자 토큰의 회원 지정 조회는 403 FORBIDDEN으로 거부된다")
    void getMemberRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();

        mvc.perform(get("/api/v1/members/{memberId}", buyerId).header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("구매자 토큰의 회원 정지는 403 FORBIDDEN으로 거부된다")
    void suspendRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        UUID targetId = registerMember();

        mvc.perform(post("/api/v1/members/{memberId}/suspend", targetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .param("reason", "FRAUD_SUSPECTED"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(memberReader.getMember(targetId).status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("미인증 회원 정지는 401 UNAUTHENTICATED로 거부된다")
    void suspendRejectsUnauthenticated() throws Exception {
        UUID targetId = registerMember();

        mvc.perform(post("/api/v1/members/{memberId}/suspend", targetId).param("reason", "FRAUD_SUSPECTED"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("관리자의 정지 후 해제 왕복이 사유를 기록했다가 지운다")
    void suspendAndReinstateRoundTrip() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(post("/api/v1/members/{memberId}/suspend", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .param("reason", "FRAUD_SUSPECTED"))
                .andExpect(status().isNoContent());
        MemberInfo suspended = memberReader.getMember(memberId);
        assertThat(suspended.status()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo(SuspensionReason.FRAUD_SUSPECTED);

        mvc.perform(post("/api/v1/members/{memberId}/reinstate", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        MemberInfo reinstated = memberReader.getMember(memberId);
        assertThat(reinstated.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reinstated.suspensionReason()).isNull();
    }

    @Test
    @DisplayName("정지 회원 담기는 409 API_MEMBER_NOT_ELIGIBLE로 거부된다")
    void suspendedMemberCannotAddCartItem() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        mvc.perform(post("/api/v1/members/{memberId}/suspend", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .param("reason", "PAYMENT_ABUSE"))
                .andExpect(status().isNoContent());

        AddCartItemRequest request = new AddCartItemRequest(variantId, 1);
        mvc.perform(post("/api/v1/carts/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_MEMBER_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("정지 회원도 탈퇴가 204로 성공한다")
    void suspendedMemberCanWithdraw() throws Exception {
        UUID memberId = registerMember();
        mvc.perform(post("/api/v1/members/{memberId}/suspend", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .param("reason", "CS_MANUAL"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .param("reason", "NO_LONGER_USED"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("활성 회원 해제는 409 MEMBER_INVALID_STATUS_TRANSITION으로 거부된다")
    void reinstateRejectsActiveMember() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(post("/api/v1/members/{memberId}/reinstate", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("본인 이름 변경이 204로 성공하고 이메일은 불변이다")
    void renameChangesNameOnly() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        MemberRenameRequest request = new MemberRenameRequest("새이름");
        mvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        MemberInfo member = memberReader.getMember(memberId);
        assertThat(member.name()).isEqualTo("새이름");
        assertThat(member.email()).isEqualTo(email);
    }

    @Test
    @DisplayName("본인 탈퇴가 204로 성공하고 회원을 논리삭제한다")
    void withdrawSoftDeletesMember() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .param("reason", "NO_LONGER_USED"))
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

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .param("reason", "NO_LONGER_USED"))
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
        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"688900123456\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/orders/{orderId}/delivery-confirmation", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .param("reason", "NO_LONGER_USED"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴 사유가 없는 요청은 400 problem+json으로 거부된다")
    void withdrawRejectsMissingReason() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("관리자의 이메일 검색은 200으로 정확 일치 활성 회원을 싣는다")
    void searchByEmailReturnsMatchingMember() throws Exception {
        String email = "search-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        mvc.perform(get("/api/v1/members").param("email", email).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("일치하는 회원이 없는 이메일 검색은 404 MEMBER_NOT_FOUND로 거부된다")
    void searchByEmailReturns404ForMissingMember() throws Exception {
        mvc.perform(get("/api/v1/members")
                        .param("email", "missing-" + UUID.randomUUID() + "@example.com")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("탈퇴 회원의 이메일 검색은 404 MEMBER_NOT_FOUND로 미존재 취급된다")
    void searchByEmailExcludesWithdrawnMember() throws Exception {
        String email = "withdrawn-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");
        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .param("reason", "NO_LONGER_USED"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/members").param("email", email).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("구매자 토큰의 이메일 검색은 403 FORBIDDEN으로 거부된다")
    void searchByEmailRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();

        mvc.perform(get("/api/v1/members")
                        .param("email", "any-" + UUID.randomUUID() + "@example.com")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 이메일 검색은 401 UNAUTHENTICATED로 거부된다")
    void searchByEmailRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/members").param("email", "any-" + UUID.randomUUID() + "@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
