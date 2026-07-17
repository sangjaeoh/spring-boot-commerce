package com.commerce.api.web.v1.admin.coupon.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 쿠폰 정책 생성 결과다. 생성된 쿠폰 ID를 문자열로 싣는다. */
@Schema(description = "쿠폰 정책 생성 결과")
public record CouponCreationResponse(
        @Schema(description = "생성된 쿠폰 ID") String couponId) {

    /** 쿠폰 ID를 문자열로 담은 응답을 만든다. */
    public static CouponCreationResponse from(UUID couponId) {
        return new CouponCreationResponse(couponId.toString());
    }
}
