package com.commerce.admin.web.v1.admin.coupon.response;

import com.commerce.coupon.application.info.CouponInfo;
import com.commerce.coupon.domain.CouponStatus;
import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    /** 쿠폰 정책 조회 모델에서 응답을 만든다. 할인 값 객체는 형별 필드로 펼쳐 싣는다. */
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
