package com.commerce.api.presentation.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.presentation.v1.request.CouponCreationRequest;
import com.commerce.api.presentation.v1.request.CouponIssuanceRequest;
import com.commerce.api.presentation.v1.request.DiscountRequest;
import com.commerce.api.presentation.v1.response.CouponCreationResponse;
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
    @DisplayName("정액 쿠폰 생성이 201로 쿠폰 ID를 반환하고 발급 가능한 쿠폰을 만든다")
    void createFixedCouponReturnsCouponId() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "정액 쿠폰", new DiscountRequest(DiscountType.FIXED, 1000L, null, null), 0L, VALID_FROM, VALID_UNTIL, 30);

        String body = mvc.perform(post("/api/v1/coupons")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponIssuanceRequest(memberId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    @Test
    @DisplayName("정률·상한 쿠폰 생성이 201로 성공한다")
    void createRateCouponWithCapSucceeds() throws Exception {
        CouponCreationRequest request = new CouponCreationRequest(
                "정률 쿠폰", new DiscountRequest(DiscountType.RATE, null, 10, 5000L), 0L, VALID_FROM, VALID_UNTIL, 30);

        mvc.perform(post("/api/v1/coupons")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_VALIDITY_PERIOD"));
    }

    @Test
    @DisplayName("발급이 201로 발급분 ID를 반환한다")
    void issueReturnsIssuedCouponId() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        CouponIssuanceRequest request = new CouponIssuanceRequest(memberId);

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issuedCouponId").exists());
    }

    @Test
    @DisplayName("정지 회원 발급은 409 API_MEMBER_NOT_ELIGIBLE로 거부된다")
    void issueRejectsSuspendedMember() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);
        CouponIssuanceRequest request = new CouponIssuanceRequest(memberId);

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_MEMBER_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("같은 회원·쿠폰 재발급은 409 COUPON_DUPLICATE_ISSUANCE로 거부된다")
    void issueRejectsDuplicate() throws Exception {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        CouponIssuanceRequest request = new CouponIssuanceRequest(memberId);
        String json = objectMapper.writeValueAsString(request);

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/{couponId}/issues", couponId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_DUPLICATE_ISSUANCE"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID createCoupon() {
        return couponAppender.create(
                "쿠폰", Discount.fixed(Money.of(1000L)), Money.ZERO, ValidityPeriod.of(VALID_FROM, VALID_UNTIL), 30);
    }
}
