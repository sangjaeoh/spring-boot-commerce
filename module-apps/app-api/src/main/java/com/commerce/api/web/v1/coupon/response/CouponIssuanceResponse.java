package com.commerce.api.web.v1.coupon.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "쿠폰 발급 결과")
public record CouponIssuanceResponse(
        @Schema(description = "발급분 ID") String issuedCouponId) {

    public static CouponIssuanceResponse from(UUID issuedCouponId) {
        return new CouponIssuanceResponse(issuedCouponId.toString());
    }
}
