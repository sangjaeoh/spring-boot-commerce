package com.commerce.coupon.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 발급 한도가 소진된 쿠폰을 발급하려 할 때 던지는 예외다. */
public class CouponExhaustedException extends BaseException {

    public CouponExhaustedException(ErrorCode errorCode) {
        super(errorCode);
    }
}
