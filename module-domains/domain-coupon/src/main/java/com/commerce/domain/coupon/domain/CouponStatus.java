package com.commerce.domain.coupon.domain;

/** 쿠폰 정책의 발급 가능 상태다. */
public enum CouponStatus {
    /** 발급 가능. 정책 생성 직후와 발급 재개가 진입시킨다. */
    ACTIVE,
    /** 발급 중지. 발급 가능 상태에서만 진입한다. */
    DISABLED
}
