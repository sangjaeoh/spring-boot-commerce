package com.commerce.api.web.v1.admin.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.coupon.request.IssuedCouponRevocationRequest;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.member.service.MemberAppender;
import com.commerce.shared.entity.Money;
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
class IssuedCouponAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponModifier issuedCouponModifier;
    private final MemberAppender memberAppender;

    IssuedCouponAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            MemberAppender memberAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("관리자 무효화가 204로 성공하고 목록 조회에 REVOKED 상태·사유가 노출된다")
    void revokeExposesRevokedStatusInList() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(post("/api/v1/admin/issued-coupons/{issuedCouponId}/revoke", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssuedCouponRevocationRequest("오발급 회수"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/issued-coupons").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(issuedId.toString()))
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andExpect(jsonPath("$[0].revokeReason").value("오발급 회수"));
    }

    @Test
    @DisplayName("사용된 발급분 무효화는 409 ISSUED_COUPON_NOT_REVOCABLE로 거부된다")
    void revokeRejectsUsedCoupon() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);
        issuedCouponModifier.use(issuedId, ownerId, UUID.randomUUID());

        mvc.perform(post("/api/v1/admin/issued-coupons/{issuedCouponId}/revoke", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssuedCouponRevocationRequest("오발급 회수"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISSUED_COUPON_NOT_REVOCABLE"));
    }

    @Test
    @DisplayName("구매자 토큰의 무효화는 403 FORBIDDEN으로 거부된다")
    void revokeRejectsBuyerToken() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(post("/api/v1/admin/issued-coupons/{issuedCouponId}/revoke", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssuedCouponRevocationRequest("오발급 회수"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("빈 사유의 무효화는 400 VALIDATION_FAILED로 거부된다")
    void revokeRejectsBlankReason() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(post("/api/v1/admin/issued-coupons/{issuedCouponId}/revoke", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssuedCouponRevocationRequest("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
