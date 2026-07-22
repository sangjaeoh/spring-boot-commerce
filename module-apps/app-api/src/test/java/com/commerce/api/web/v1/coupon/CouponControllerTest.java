package com.commerce.api.web.v1.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.coupon.response.IssuableCouponPageResponse;
import com.commerce.api.web.v1.coupon.response.IssuableCouponResponse;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.CouponModifier;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.shared.entity.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    private final CouponModifier couponModifier;
    private final IssuedCouponAppender issuedCouponAppender;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;

    CouponControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            CouponModifier couponModifier,
            IssuedCouponAppender issuedCouponAppender,
            MemberAppender memberAppender,
            MemberModifier memberModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.couponAppender = couponAppender;
        this.couponModifier = couponModifier;
        this.issuedCouponAppender = issuedCouponAppender;
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
        UUID couponId = couponAppender.create(
                "한도 쿠폰",
                Discount.fixed(Money.of(1000L)),
                Money.ZERO,
                ValidityPeriod.of(VALID_FROM, VALID_UNTIL),
                30,
                1);

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ISSUANCE_LIMIT_EXHAUSTED"));
    }

    @Test
    @DisplayName("목록 조회가 발급 가능한 쿠폰만 최신 등록순으로 반환한다")
    void listReturnsOnlyIssuableCoupons() throws Exception {
        UUID memberId = registerMember();
        UUID disabledId = createCoupon();
        couponModifier.disable(disabledId);
        UUID outsideWindowId = couponAppender.create(
                "기간 밖 쿠폰",
                Discount.fixed(Money.of(1000L)),
                Money.ZERO,
                ValidityPeriod.of(Instant.parse("2031-01-01T00:00:00Z"), Instant.parse("2032-01-01T00:00:00Z")),
                30,
                null);
        UUID exhaustedId = couponAppender.create(
                "소진 쿠폰",
                Discount.fixed(Money.of(1000L)),
                Money.ZERO,
                ValidityPeriod.of(VALID_FROM, VALID_UNTIL),
                30,
                1);
        issuedCouponAppender.issue(exhaustedId, memberId);
        UUID issuableId = createCoupon();

        String body = mvc.perform(get("/api/v1/coupons").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].id").value(issuableId.toString()))
                .andExpect(jsonPath("$.coupons[0].name").value("쿠폰"))
                .andExpect(jsonPath("$.coupons[0].discountType").value("FIXED"))
                .andExpect(jsonPath("$.coupons[0].discountAmount").value(1000))
                .andExpect(jsonPath("$.coupons[0].minOrderAmount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = objectMapper.readValue(body, IssuableCouponPageResponse.class).coupons().stream()
                .map(IssuableCouponResponse::id)
                .toList();
        assertThat(ids).contains(issuableId).doesNotContain(disabledId, outsideWindowId, exhaustedId);
    }

    @Test
    @DisplayName("목록 조회가 페이지 파라미터대로 최신 등록순 페이지를 반환한다")
    void listPaginatesNewestFirst() throws Exception {
        UUID memberId = registerMember();
        UUID olderId = createCoupon();
        UUID newerId = createCoupon();

        mvc.perform(get("/api/v1/coupons")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons.length()").value(1))
                .andExpect(jsonPath("$.coupons[0].id").value(newerId.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(1));

        mvc.perform(get("/api/v1/coupons")
                        .param("page", "2")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].id").value(olderId.toString()))
                .andExpect(jsonPath("$.page.number").value(2));
    }

    @Test
    @DisplayName("미인증 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void listRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/coupons"))
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
}
