package com.commerce.api.presentation.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.service.MemberAppender;
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
                30);
    }
}
