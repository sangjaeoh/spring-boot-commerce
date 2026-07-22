package com.commerce.admin.web.v1.admin.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.web.v1.WebIntegrationTest;
import com.commerce.admin.web.v1.admin.coupon.request.IssuedCouponRevocationRequest;
import com.commerce.coupon.application.info.IssuedCouponInfo;
import com.commerce.coupon.application.provided.CouponAppender;
import com.commerce.coupon.application.provided.IssuedCouponAppender;
import com.commerce.coupon.application.provided.IssuedCouponModifier;
import com.commerce.coupon.application.provided.IssuedCouponReader;
import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.IssuedCouponStatus;
import com.commerce.coupon.domain.ValidityPeriod;
import com.commerce.member.application.provided.MemberAppender;
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
    private final IssuedCouponReader issuedCouponReader;
    private final MemberAppender memberAppender;

    IssuedCouponAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            IssuedCouponReader issuedCouponReader,
            MemberAppender memberAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.issuedCouponReader = issuedCouponReader;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("관리자 무효화가 204로 성공하고 조회에 REVOKED 상태·사유가 노출된다")
    void revokeExposesRevokedStatusInList() throws Exception {
        UUID ownerId = registerMember();
        UUID issuedId = issuedCouponAppender.issue(createCoupon(), ownerId);

        mvc.perform(post("/api/v1/admin/issued-coupons/{issuedCouponId}/revoke", issuedId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssuedCouponRevocationRequest("오발급 회수"))))
                .andExpect(status().isNoContent());

        IssuedCouponInfo revoked = issuedCouponReader.getIssuedCoupon(issuedId, ownerId);
        assertThat(revoked.status()).isEqualTo(IssuedCouponStatus.REVOKED);
        assertThat(revoked.revokeReason()).isEqualTo("오발급 회수");
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
