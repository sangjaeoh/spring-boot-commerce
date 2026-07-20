package com.commerce.api.web.v1.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.shared.entity.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IssuedCouponControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final MemberAppender memberAppender;

    IssuedCouponControllerTest(
            MockMvc mvc,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            MemberAppender memberAppender) {
        this.mvc = mvc;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("본인 발급분 조회는 200으로 ISSUED 상태와 발급분 ID를 싣는다")
    void getIssuedCouponReturnsDetail() throws Exception {
        UUID ownerId = registerMember();
        UUID couponId = createCoupon();
        UUID issuedId = issuedCouponAppender.issue(couponId, ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(issuedId.toString()))
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    @DisplayName("타인 발급분 조회는 404 ISSUED_COUPON_NOT_FOUND로 거부된다")
    void getIssuedCouponRejectsNonOwner() throws Exception {
        UUID ownerId = registerMember();
        UUID otherId = registerMember();
        UUID couponId = createCoupon();
        UUID issuedId = issuedCouponAppender.issue(couponId, ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUED_COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("할인 미리보기는 200으로 적용 가능 여부와 예상 할인액을 싣는다")
    void getDiscountPreviewReturnsExpectedDiscount() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}/discount-preview", issuedId)
                        .param("orderAmount", "15000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.discountAmount").value(1000));
    }

    @Test
    @DisplayName("최소주문금액 미달 할인 미리보기는 200으로 적용 불가와 사유·0원을 싣는다")
    void getDiscountPreviewRepresentsMinOrderShortfall() throws Exception {
        UUID ownerId = registerMember();
        UUID couponId = couponAppender.create(
                "정률 10% (상한 2000)",
                Discount.rate(10, Money.of(2000L)),
                Money.of(10000L),
                ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z")),
                30,
                null);
        UUID issuedId = issuedCouponAppender.issue(couponId, ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}/discount-preview", issuedId)
                        .param("orderAmount", "9999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.reason").value("MIN_ORDER_AMOUNT_NOT_MET"))
                .andExpect(jsonPath("$.discountAmount").value(0));
    }

    @Test
    @DisplayName("타인 발급분 할인 미리보기는 404 ISSUED_COUPON_NOT_FOUND로 거부된다")
    void getDiscountPreviewRejectsNonOwner() throws Exception {
        UUID ownerId = registerMember();
        UUID otherId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}/discount-preview", issuedId)
                        .param("orderAmount", "15000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUED_COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("범위 밖 주문 금액(음수·상한 초과)의 할인 미리보기는 400 VALIDATION_FAILED로 거부된다")
    void getDiscountPreviewRejectsOutOfRangeOrderAmount() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}/discount-preview", issuedId)
                        .param("orderAmount", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/v1/issued-coupons/{issuedCouponId}/discount-preview", issuedId)
                        .param("orderAmount", "1000000000000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("미인증 발급 쿠폰 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void getIssuedCouponsRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/issued-coupons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("발급 쿠폰 목록 조회는 토큰 주체의 발급분만 최신순으로 싣는다")
    void getIssuedCouponsReturnsOnlyOwnCouponsNewestFirst() throws Exception {
        UUID ownerId = registerMember();
        UUID otherId = registerMember();
        UUID firstIssuedId = issuedCouponAppender.issue(createCoupon(), ownerId);
        UUID secondIssuedId = issuedCouponAppender.issue(createCoupon(), ownerId);
        issuedCouponAppender.issue(createCoupon(), otherId);

        mvc.perform(get("/api/v1/issued-coupons").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondIssuedId.toString()))
                .andExpect(jsonPath("$[1].id").value(firstIssuedId.toString()));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID createCoupon() {
        return couponAppender.create(
                "쿠폰",
                Discount.fixed(Money.of(1000L)),
                Money.ZERO,
                ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z")),
                30,
                null);
    }
}
