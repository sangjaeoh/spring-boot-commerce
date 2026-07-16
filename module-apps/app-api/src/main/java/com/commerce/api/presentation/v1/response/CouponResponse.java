package com.commerce.api.presentation.v1.response;

import com.commerce.coupon.entity.CouponStatus;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import com.commerce.coupon.info.CouponInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 응답이다. 판별 형 할인·발급 가능 기간·발급 한도·소진 카운트·발급 상태를 싣는다. */
public record CouponResponse(
        UUID id,
        String name,
        DiscountType discountType,
        @Nullable Long discountAmount,
        @Nullable Integer discountPercent,
        @Nullable Long discountMaxCap,
        long minOrderAmount,
        Instant validFrom,
        Instant validUntil,
        int usageValidDays,
        @Nullable Integer maxIssuance,
        int issuedCount,
        CouponStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static CouponResponse from(CouponInfo coupon) {
        Discount discount = coupon.discount();
        return new CouponResponse(
                coupon.id(),
                coupon.name(),
                discount.type(),
                discount.amount() == null ? null : discount.amount().amount(),
                discount.percent(),
                discount.maxCap() == null ? null : discount.maxCap().amount(),
                coupon.minOrderAmount().amount(),
                coupon.validity().validFrom(),
                coupon.validity().validUntil(),
                coupon.usageValidDays(),
                coupon.maxIssuance(),
                coupon.issuedCount(),
                coupon.status(),
                coupon.createdAt(),
                coupon.updatedAt());
    }
}
