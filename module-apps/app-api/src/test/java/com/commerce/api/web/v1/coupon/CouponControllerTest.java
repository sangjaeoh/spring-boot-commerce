package com.commerce.api.web.v1.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.coupon.request.CouponCreationRequest;
import com.commerce.api.web.v1.admin.coupon.request.DiscountRequest;
import com.commerce.api.web.v1.admin.coupon.response.CouponCreationResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import java.time.Instant;
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
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;

    CouponControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            MemberAppender memberAppender,
            MemberModifier memberModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.couponAppender = couponAppender;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
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
    @DisplayName("발급 한도 소진 후 발급은 409 COUPON_ISSUANCE_LIMIT_EXHAUSTED로 거부된다")
    void issueRejectsWhenLimitExhausted() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "한도 쿠폰",
                new DiscountRequest(DiscountType.FIXED, 1000L, null, null),
                0L,
                VALID_FROM,
                VALID_UNTIL,
                30,
                1);
        String body = mvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID couponId = UUID.fromString(
                objectMapper.readValue(body, CouponCreationResponse.class).couponId());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ISSUANCE_LIMIT_EXHAUSTED"));
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
}
