package com.commerce.coupon.application.info;

import com.commerce.coupon.domain.Coupon;
import com.commerce.coupon.domain.CouponStatus;
import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.ValidityPeriod;
import com.commerce.shared.entity.Money;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 조회 경계 모델이다. */
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

    /** 쿠폰 정책 엔티티에서 조회 모델을 만든다. */
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
