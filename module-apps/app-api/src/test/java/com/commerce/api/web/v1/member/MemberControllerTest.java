package com.commerce.api.web.v1.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.cart.request.AddCartItemRequest;
import com.commerce.api.web.v1.member.request.EmailVerificationRequest;
import com.commerce.api.web.v1.member.request.LoginRequest;
import com.commerce.api.web.v1.member.request.MemberPasswordReplacementRequest;
import com.commerce.api.web.v1.member.request.MemberRegistrationRequest;
import com.commerce.api.web.v1.member.request.MemberRenameRequest;
import com.commerce.api.web.v1.member.request.MemberWithdrawalRequest;
import com.commerce.api.web.v1.member.response.MemberRegistrationResponse;
import com.commerce.cart.application.provided.CartAppender;
import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.member.application.provided.MemberModifier;
import com.commerce.member.application.provided.MemberReader;
import com.commerce.member.application.required.MailGateway;
import com.commerce.member.domain.MemberNotFoundException;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.domain.SuspensionReason;
import com.commerce.member.domain.WithdrawalReason;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.domain.Address;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberControllerTest extends WebIntegrationTest {

    // RateLimitConfig가 로그인·가입 표면에 공통으로 적용하는 창당 한도. 초과 시도가 429로 거부되는지 검증한다.
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final String SIGNUP_PATH = "/api/v1/members";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @MockitoSpyBean
    private MailGateway mailGateway;

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final MemberReader memberReader;
    private final CartAppender cartAppender;
    private final ProductVariantReader variantReader;
    private final CheckoutFacade checkoutFacade;
    private final OrderModifier orderModifier;

    MemberControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            MemberReader memberReader,
            CartAppender cartAppender,
            ProductVariantReader variantReader,
            CheckoutFacade checkoutFacade,
            OrderModifier orderModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.memberReader = memberReader;
        this.cartAppender = cartAppender;
        this.variantReader = variantReader;
        this.checkoutFacade = checkoutFacade;
        this.orderModifier = orderModifier;
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
    @DisplayName("한 창 안 가입 시도가 한도를 넘으면 429 TOO_MANY_SIGNUP_ATTEMPTS로 거부되고 한도 내 가입은 201로 성공한다")
    void registerRejectsAfterTooManyAttempts() throws Exception {
        // 다른 테스트가 공유하는 기본 클라이언트 IP를 오염시키지 않도록 전용 IP로 한도를 채운다.
        String clientIp = "203.0.113.20";

        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_WINDOW; attempt++) {
            mvc.perform(postFrom(SIGNUP_PATH, clientIp, uniqueRegistration())).andExpect(status().isCreated());
        }

        mvc.perform(postFrom(SIGNUP_PATH, clientIp, uniqueRegistration()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "300"))
                // 필터 단계에서 거부된 응답에도 시큐리티 헤더가 실린다.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_SIGNUP_ATTEMPTS"));
    }

    @Test
    @DisplayName("같은 IP의 로그인 한도 소진이 가입 한도를 소모하지 않는다(표면별 카운터 분리)")
    void signupThrottleIsIsolatedFromLogin() throws Exception {
        String clientIp = "203.0.113.21";
        String loginBody = objectMapper.writeValueAsString(
                new LoginRequest("nobody-" + UUID.randomUUID() + "@example.com", "password-123!"));

        // 1. 같은 IP의 로그인 카운터 한도 초과 소진
        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_WINDOW; attempt++) {
            mvc.perform(postFrom(LOGIN_PATH, clientIp, loginBody)).andExpect(status().isUnauthorized());
        }
        mvc.perform(postFrom(LOGIN_PATH, clientIp, loginBody)).andExpect(status().isTooManyRequests());

        // 2. 같은 IP의 가입 요청
        // 가입은 별도 카운터라 로그인 소진에 영향받지 않는다.
        mvc.perform(postFrom(SIGNUP_PATH, clientIp, uniqueRegistration())).andExpect(status().isCreated());
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
    @DisplayName("정지 회원 담기는 409 API_MEMBER_NOT_ELIGIBLE로 거부된다")
    void suspendedMemberCannotAddCartItem() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        memberModifier.suspend(memberId, SuspensionReason.PAYMENT_ABUSE);

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
        memberModifier.suspend(memberId, SuspensionReason.CS_MANUAL);

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberWithdrawalRequest(WithdrawalReason.NO_LONGER_USED))))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
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
    @DisplayName("본인 비밀번호 변경이 204로 성공하고 새 비밀번호로만 로그인된다")
    void replacePasswordSwitchesLoginCredential() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        MemberPasswordReplacementRequest request =
                new MemberPasswordReplacementRequest("password-123!", "new-password-456!");
        mvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "new-password-456!"))))
                .andExpect(status().isOk());

        mvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password-123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("현재 비밀번호가 틀린 변경은 400 MEMBER_PASSWORD_MISMATCH로 거부된다")
    void replacePasswordRejectsWrongCurrentPassword() throws Exception {
        UUID memberId = registerMember();

        MemberPasswordReplacementRequest request =
                new MemberPasswordReplacementRequest("wrong-password!", "new-password-456!");
        mvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("8자 미만 새 비밀번호 변경은 400 MEMBER_INVALID_PASSWORD_FORMAT로 거부된다")
    void replacePasswordRejectsShortNewPassword() throws Exception {
        UUID memberId = registerMember();

        MemberPasswordReplacementRequest request = new MemberPasswordReplacementRequest("password-123!", "short-7");
        mvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_PASSWORD_FORMAT"));
    }

    @Test
    @DisplayName("미인증 비밀번호 변경은 401 UNAUTHENTICATED로 거부된다")
    void replacePasswordRejectsUnauthenticated() throws Exception {
        MemberPasswordReplacementRequest request =
                new MemberPasswordReplacementRequest("password-123!", "new-password-456!");
        mvc.perform(patch("/api/v1/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("본인 탈퇴가 204로 성공하고 회원을 논리삭제한다")
    void withdrawSoftDeletesMember() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberWithdrawalRequest(WithdrawalReason.NO_LONGER_USED))))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberWithdrawalRequest(WithdrawalReason.NO_LONGER_USED))))
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
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberWithdrawalRequest(WithdrawalReason.NO_LONGER_USED))))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴 사유가 없는 요청은 400 VALIDATION_FAILED 구조 응답으로 거부된다")
    void withdrawRejectsMissingReason() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("가입이 인증 메일 토큰을 발급하고 검증하면 본인 조회에 인증 시각이 실린다")
    void registrationIssuesVerificationTokenAndVerificationRecordsTimestamp() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberRegistrationRequest(email, "테스터", "password-123!"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID memberId = UUID.fromString(
                objectMapper.readValue(body, MemberRegistrationResponse.class).memberId());
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailGateway).sendVerificationMail(eq(email), tokenCaptor.capture());

        mvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerifiedAt").doesNotExist());

        mvc.perform(post("/api/v1/members/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailVerificationRequest(tokenCaptor.getValue()))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerifiedAt").exists());
    }

    @Test
    @DisplayName("위조·재사용 인증 토큰은 401 UNAUTHENTICATED로 거부된다")
    void emailVerificationRejectsForgedAndReusedTokens() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberRegistrationRequest(email, "테스터", "password-123!"))))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailGateway).sendVerificationMail(eq(email), tokenCaptor.capture());
        mvc.perform(post("/api/v1/members/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailVerificationRequest(tokenCaptor.getValue()))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/members/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailVerificationRequest(tokenCaptor.getValue()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/members/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmailVerificationRequest(UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private String uniqueRegistration() {
        return objectMapper.writeValueAsString(
                new MemberRegistrationRequest("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!"));
    }

    private static MockHttpServletRequestBuilder postFrom(String path, String clientIp, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body).with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
    }

    private UUID seedProduct(int quantity) {
        UUID productId = seedOnSaleProduct("상품", null, Money.of(10000L), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
