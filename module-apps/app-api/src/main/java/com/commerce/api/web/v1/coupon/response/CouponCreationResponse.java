package com.commerce.api.web.v1.coupon.response;

import java.util.UUID;

/** 쿠폰 정책 생성 결과다. 생성된 쿠폰 ID를 문자열로 싣는다. */
public record CouponCreationResponse(String couponId) {

    /** 쿠폰 ID를 문자열로 담은 응답을 만든다. */
    public static CouponCreationResponse from(UUID couponId) {
        return new CouponCreationResponse(couponId.toString());
    }
}
