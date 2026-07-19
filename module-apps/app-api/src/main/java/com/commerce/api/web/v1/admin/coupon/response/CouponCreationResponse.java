package com.commerce.api.web.v1.admin.coupon.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "쿠폰 정책 생성 결과")
public record CouponCreationResponse(
        @Schema(description = "생성된 쿠폰 ID") String couponId) {

    public static CouponCreationResponse from(UUID couponId) {
        return new CouponCreationResponse(couponId.toString());
    }
}
