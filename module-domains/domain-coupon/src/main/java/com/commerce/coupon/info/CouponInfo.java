package com.commerce.coupon.info;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.entity.CouponStatus;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 조회 경계 모델이다. 할인·발급 가능 기간·발급 한도·소진 카운트·발급 상태를 함께 싣는다. */
public record CouponInfo(
        UUID id,
        String name,
        Discount discount,
        Money minOrderAmount,
        ValidityPeriod validity,
        int usageValidDays,
        @Nullable Integer maxIssuance,
        int issuedCount,
        CouponStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscount(),
                coupon.getMinOrderAmount(),
                coupon.getValidity(),
                coupon.getUsageValidDays(),
                coupon.getMaxIssuance(),
                coupon.getIssuedCount(),
                coupon.getStatus(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt());
    }
}
