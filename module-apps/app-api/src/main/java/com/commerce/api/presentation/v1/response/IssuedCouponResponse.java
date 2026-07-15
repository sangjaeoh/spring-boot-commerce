package com.commerce.api.presentation.v1.response;

import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.info.IssuedCouponInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 발급 쿠폰 상세 응답이다. 사용 상태·사용 만료 시각과 사용 시각·주문을 싣는다. */
public record IssuedCouponResponse(
        UUID id,
        UUID couponId,
        UUID memberId,
        IssuedCouponStatus status,
        Instant expiresAt,
        @Nullable Instant usedAt,
        @Nullable UUID orderId) {

    public static IssuedCouponResponse from(IssuedCouponInfo issued) {
        return new IssuedCouponResponse(
                issued.id(),
                issued.couponId(),
                issued.memberId(),
                issued.status(),
                issued.expiresAt(),
                issued.usedAt(),
                issued.orderId());
    }
}
