package com.commerce.coupon.entity;

/**
 * 발급 쿠폰의 사용 상태다. {@code REVOKED}는 관리자 무효화로 진입하는 재전이 없는 종료 상태다.
 */
public enum IssuedCouponStatus {
    ISSUED,
    USED,
    REVOKED
}
