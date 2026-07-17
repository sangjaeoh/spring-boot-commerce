package com.commerce.api.web.v1.coupon.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 쿠폰 발급 결과다. 발급분 ID를 문자열로 싣는다. */
@Schema(description = "쿠폰 발급 결과")
public record CouponIssuanceResponse(
        @Schema(description = "발급분 ID") String issuedCouponId) {

    /** 발급분 ID를 문자열로 담은 응답을 만든다. */
    public static CouponIssuanceResponse from(UUID issuedCouponId) {
        return new CouponIssuanceResponse(issuedCouponId.toString());
    }
}
