package com.commerce.api.presentation.v1.response;

import java.util.UUID;

/** 쿠폰 발급 결과다. 발급분 ID를 문자열로 싣는다. */
public record CouponIssuanceResponse(String issuedCouponId) {

    /** 발급분 ID를 문자열로 담은 응답을 만든다. */
    public static CouponIssuanceResponse from(UUID issuedCouponId) {
        return new CouponIssuanceResponse(issuedCouponId.toString());
    }
}
