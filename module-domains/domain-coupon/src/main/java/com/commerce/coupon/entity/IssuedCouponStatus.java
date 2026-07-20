package com.commerce.coupon.entity;

/** 발급분의 사용 상태다. */
public enum IssuedCouponStatus {
    /** 발급됨. 발급 직후와 사용 복원이 진입시킨다. */
    ISSUED,
    /** 사용 완료. 발급된 상태에서만 진입한다. */
    USED,
    /** 관리자 무효화. 발급된 상태에서만 진입하는 재전이 없는 종료 상태. */
    REVOKED
}
