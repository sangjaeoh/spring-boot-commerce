package com.commerce.api.web.v1.admin.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.coupon.request.CouponCreationRequest;
import com.commerce.api.web.v1.admin.coupon.request.DiscountRequest;
import com.commerce.api.web.v1.admin.coupon.response.CouponCreationResponse;
import com.commerce.cart.service.CartAppender;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
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
class CouponAdminControllerTest extends WebIntegrationTest {

    private static final Instant VALID_FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2030-01-01T00:00:00Z");

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponReader issuedCouponReader;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final CheckoutFacade checkoutFacade;

    CouponAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponReader issuedCouponReader,
            MemberAppender memberAppender,
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
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.checkoutFacade = checkoutFacade;
    }

    @Test
    @DisplayName("정액 쿠폰 생성이 201로 쿠폰 ID를 반환하고 발급 가능한 쿠폰을 만든다")
    void createFixedCouponReturnsCouponId() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "정액 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        String body = mvc.perform(post("/api/v1/admin/coupons")
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
                "정률 쿠폰",
                new DiscountRequest(DiscountType.RATE, null, 10, 5000L),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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
                "  ",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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
                "잘못된 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, 10, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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
                "음수 상한 쿠폰",
                new DiscountRequest(DiscountType.RATE, null, 10, -1L),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_VALIDITY_PERIOD"));
    }

    @Test
    @DisplayName("발급 한도가 1 미만이면 400 VALIDATION_FAILED로 거부된다")
    void createRejectsMaxIssuanceBelowOne() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "한도 오류 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                0);

        mvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("구매자 토큰의 쿠폰 정책 생성은 403 FORBIDDEN으로 거부된다")
    void createRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        CouponCreationRequest request = new CouponCreationRequest(
                "정액 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                null);

        mvc.perform(post("/api/v1/admin/coupons")
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

        mvc.perform(post("/api/v1/admin/coupons/{couponId}/disable", couponId)
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

        mvc.perform(post("/api/v1/admin/coupons/{couponId}/disable", couponId)
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

        mvc.perform(post("/api/v1/admin/coupons/{couponId}/disable", couponId)
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
        mvc.perform(post("/api/v1/admin/coupons/{couponId}/disable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/admin/coupons/{couponId}/enable", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    @Test
    @DisplayName("관리자 쿠폰 정책 목록은 200으로 정책을 최신순 페이지·할인·상태와 함께 싣는다")
    void couponPolicyListReturnsPoliciesNewestFirst() throws Exception {
        UUID couponId = createCoupon();

        mvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].id").value(couponId.toString()))
                .andExpect(jsonPath("$.coupons[0].name").value("쿠폰"))
                .andExpect(jsonPath("$.coupons[0].discountType").value("FIXED"))
                .andExpect(jsonPath("$.coupons[0].discountAmount").value(1000))
                .andExpect(jsonPath("$.coupons[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());
    }

    @Test
    @DisplayName("구매자 토큰의 쿠폰 정책 목록 조회는 403 FORBIDDEN으로 거부된다")
    void couponPolicyListRejectsBuyerToken() throws Exception {
        mvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 쿠폰 정책 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void couponPolicyListRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/coupons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("정책별 발급분 목록은 200으로 해당 정책의 발급분만 최신순 페이지로 싣는다")
    void issuedCouponListByCouponReturnsOnlyThatPolicysIssuances() throws Exception {
        UUID couponId = createCoupon();
        UUID otherCouponId = createCoupon();
        UUID issuedA = issuedCouponAppender.issue(couponId, registerMember());
        UUID issuedB = issuedCouponAppender.issue(couponId, registerMember());
        issuedCouponAppender.issue(otherCouponId, registerMember());

        mvc.perform(get("/api/v1/admin/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedCoupons.length()").value(2))
                .andExpect(jsonPath("$.issuedCoupons[0].id").value(issuedB.toString()))
                .andExpect(jsonPath("$.issuedCoupons[1].id").value(issuedA.toString()))
                .andExpect(jsonPath("$.issuedCoupons[0].couponId").value(couponId.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    @DisplayName("구매자 토큰의 정책별 발급분 목록 조회는 403 FORBIDDEN으로 거부된다")
    void issuedCouponListByCouponRejectsBuyerToken() throws Exception {
        UUID couponId = createCoupon();

        mvc.perform(get("/api/v1/admin/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 정책별 발급분 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void issuedCouponListByCouponRejectsUnauthenticated() throws Exception {
        UUID couponId = createCoupon();

        mvc.perform(get("/api/v1/admin/coupons/{couponId}/issues", couponId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID createCoupon() {
        return couponAppender.create(
                "쿠폰",
                Discount.fixed(Money.of(1000L)),
                Money.ZERO,
                ValidityPeriod.of(VALID_FROM, VALID_UNTIL),
                30,
                null);
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}
