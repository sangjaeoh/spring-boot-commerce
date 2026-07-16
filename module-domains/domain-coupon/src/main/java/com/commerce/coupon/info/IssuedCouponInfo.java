package com.commerce.coupon.info;

import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.entity.IssuedCouponStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 발급 쿠폰 조회 경계 모델이다. */
public record IssuedCouponInfo(
        UUID id,
        UUID couponId,
        UUID memberId,
        IssuedCouponStatus status,
        Instant expiresAt,
        @Nullable Instant usedAt,
        @Nullable UUID orderId,
        @Nullable Instant revokedAt,
        @Nullable String revokeReason,
        Instant createdAt,
        Instant updatedAt) {

    public static IssuedCouponInfo from(IssuedCoupon issued) {
        return new IssuedCouponInfo(
                issued.getId(),
                issued.getCouponId(),
                issued.getMemberId(),
                issued.getStatus(),
                issued.getExpiresAt(),
                issued.getUsedAt(),
                issued.getOrderId(),
                issued.getRevokedAt(),
                issued.getRevokeReason(),
                issued.getCreatedAt(),
                issued.getUpdatedAt());
    }
}
