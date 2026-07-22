package com.commerce.coupon.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 쿠폰 정책을 찾지 못했을 때 던지는 예외다. */
public class CouponNotFoundException extends BaseException {

    public CouponNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
