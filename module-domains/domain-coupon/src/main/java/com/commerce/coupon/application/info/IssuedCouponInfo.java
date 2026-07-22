package com.commerce.coupon.application.info;

import com.commerce.coupon.domain.IssuedCoupon;
import com.commerce.coupon.domain.IssuedCouponStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 발급분 조회 경계 모델이다. */
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

    /** 발급분 엔티티에서 조회 모델을 만든다. */
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
