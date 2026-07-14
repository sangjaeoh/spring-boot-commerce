package com.commerce.coupon.entity;

/** 발급 쿠폰의 사용 상태다. 만료는 상태가 아니라 expiresAt 경과로 파생 판정한다. */
public enum IssuedCouponStatus {
    ISSUED,
    USED
}
