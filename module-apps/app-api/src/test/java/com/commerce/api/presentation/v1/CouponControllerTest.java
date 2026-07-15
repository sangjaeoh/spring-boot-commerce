package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.CouponCreationRequest;
import com.commerce.api.presentation.v1.request.DiscountRequest;
import com.commerce.api.presentation.v1.response.CouponCreationResponse;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.order.entity.Address;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import java.time.Instant;
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
class CouponControllerTest extends WebIntegrationTest {

    private static final Instant VALID_FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2030-01-01T00:00:00Z");

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponReader issuedCouponReader;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final CheckoutFacade checkoutFacade;

    CouponControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponReader issuedCouponReader,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            CheckoutFacade checkoutFacade) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponReader = issuedCouponReader;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.checkoutFacade = checkoutFacade;
    }

    @Test
    @DisplayName("정액 쿠폰 생성이 201로 쿠폰 ID를 반환하고 발급 가능한 쿠폰을 만든다")
    void createFixedCouponReturnsCouponId() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "정액 쿠폰", new DiscountRequest(DiscountType.FIXED, 1000L, null, null), 0L, VALID_FROM, VALID_UNTIL, 30);

        String body = mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID couponId = UUID.fromString(
                objectMapper.readValue(body, CouponCreationResponse.class).couponId());
        UUID memberId = registerMember();
        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    @Test
    @DisplayName("정률·상한 쿠폰 생성이 201로 성공한다")
    void createRateCouponWithCapSucceeds() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "정률 쿠폰", new DiscountRequest(DiscountType.RATE, null, 10, 5000L), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").exists());
    }

    @Test
    @DisplayName("빈 쿠폰명은 400 problem+json으로 거부된다")
    void createRejectsBlankName() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "  ", new DiscountRequest(DiscountType.FIXED, 1000L, null, null), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("잘못된 할인 조합은 400 COUPON_INVALID_DISCOUNT로 거부된다")
    void createRejectsInvalidDiscountCombination() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "잘못된 쿠폰", new DiscountRequest(DiscountType.FIXED, 1000L, 10, null), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_DISCOUNT"));
    }

    @Test
    @DisplayName("음수 정액 할인액은 400 VALIDATION_FAILED로 거부된다")
    void createRejectsNegativeDiscountAmount() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "음수 할인 쿠폰",
                new DiscountRequest(DiscountType.FIXED, -1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("음수 정률 상한은 400 VALIDATION_FAILED로 거부된다")
    void createRejectsNegativeMaxCap() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "음수 상한 쿠폰", new DiscountRequest(DiscountType.RATE, null, 10, -1L), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("발급 시작이 종료 이후면 400 COUPON_INVALID_VALIDITY_PERIOD로 거부된다")
    void createRejectsInvalidValidityPeriod() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "기간 오류 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_UNTIL,
                VALID_FROM,
                30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_VALIDITY_PERIOD"));
    }

    @Test
    @DisplayName("발급이 201로 토큰 주체에게 발급하고 발급분 ID를 반환한다")
    void issueReturnsIssuedCouponId() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    @Test
    @DisplayName("미인증 발급은 401 UNAUTHENTICATED로 거부된다")
    void issueRejectsUnauthenticated() throws Exception {
        UUID couponId = createCoupon();

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("정지 회원 발급은 409 API_MEMBER_NOT_ELIGIBLE로 거부된다")
    void issueRejectsSuspendedMember() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_MEMBER_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("같은 회원·쿠폰 재발급은 409 COUPON_DUPLICATE_ISSUANCE로 거부된다")
    void issueRejectsDuplicate() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_DUPLICATE_ISSUANCE"));
    }

    @Test
    @DisplayName("구매자 토큰의 쿠폰 정책 생성은 403 FORBIDDEN으로 거부된다")
    void createRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        CouponCreationRequest request = new CouponCreationRequest(
                "정액 쿠폰", new DiscountRequest(DiscountType.FIXED, 1000L, null, null), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("구매자 토큰의 쿠폰 정책 중지는 403 FORBIDDEN으로 거부되고 발급은 계속된다")
    void disableRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        UUID couponId = createCoupon();

        mvc.perform(post("/api/v1/coupons/{couponId}/disable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("중지가 204로 성공하고 이후 발급은 409 COUPON_DISABLED로 거부된다")
    void disableRejectsNewIssuance() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();

        mvc.perform(post("/api/v1/coupons/{couponId}/disable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_DISABLED"));
    }

    @Test
    @DisplayName("중지 전 발급분은 중지 후에도 체크아웃에서 사용된다")
    void disableDoesNotAffectAlreadyIssuedCoupon() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);

        mvc.perform(post("/api/v1/coupons/{couponId}/disable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.USED);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).orderId())
                .isEqualTo(orderId);
    }

    @Test
    @DisplayName("중지 후 재개가 204로 성공하고 발급이 재개된다")
    void enableResumesIssuance() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        mvc.perform(post("/api/v1/coupons/{couponId}/disable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/coupons/{couponId}/enable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID createCoupon() {
        return couponAppender.create(
                "쿠폰", Discount.fixed(Money.of(1000L)), Money.ZERO, ValidityPeriod.of(VALID_FROM, VALID_UNTIL), 30);
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
