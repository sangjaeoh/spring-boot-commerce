package com.commerce.api.web.v1.admin.coupon.response;

import com.commerce.coupon.entity.CouponStatus;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import com.commerce.coupon.info.CouponInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 응답이다. 판별 형 할인·발급 가능 기간·발급 한도·소진 카운트·발급 상태를 싣는다. */
@Schema(description = "쿠폰 정책 응답")
public record CouponResponse(
        @Schema(description = "쿠폰 ID") UUID id,
        @Schema(description = "쿠폰명") String name,
        @Schema(description = "할인 형(정액 FIXED·정률 RATE)") DiscountType discountType,

        @Schema(description = "정액 할인액(원 단위)", nullable = true) @Nullable
        Long discountAmount,

        @Schema(description = "할인율(%)", nullable = true) @Nullable
        Integer discountPercent,

        @Schema(description = "정률 할인 상한(원 단위)", nullable = true) @Nullable
        Long discountMaxCap,

        @Schema(description = "최소 주문 금액(원 단위)") long minOrderAmount,
        @Schema(description = "발급 가능 시작 시각") Instant validFrom,
        @Schema(description = "발급 가능 종료 시각") Instant validUntil,
        @Schema(description = "사용 창(일)") int usageValidDays,

        @Schema(description = "발급 한도(없으면 무제한)", nullable = true) @Nullable
        Integer maxIssuance,

        @Schema(description = "발급 수(소진 카운트)") int issuedCount,
        @Schema(description = "발급 상태") CouponStatus status,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

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
