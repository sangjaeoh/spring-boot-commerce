package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.info.IssuedCouponInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 발급 쿠폰 상세 응답이다. 사용 상태·사용 만료 시각과 사용 시각·주문, 무효화 시각·사유를 싣는다. */
public record IssuedCouponResponse(
        UUID id,
        UUID couponId,
        UUID memberId,
        IssuedCouponStatus status,
        Instant expiresAt,
        @Nullable Instant usedAt,
        @Nullable UUID orderId,
        @Nullable Instant revokedAt,
        @Nullable String revokeReason) {

    public static IssuedCouponResponse from(IssuedCouponInfo issued) {
        return new IssuedCouponResponse(
                issued.id(),
                issued.couponId(),
                issued.memberId(),
                issued.status(),
                issued.expiresAt(),
                issued.usedAt(),
                issued.orderId(),
                issued.revokedAt(),
                issued.revokeReason());
    }
}
