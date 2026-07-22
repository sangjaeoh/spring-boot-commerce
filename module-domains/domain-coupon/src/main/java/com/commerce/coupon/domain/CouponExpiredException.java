package com.commerce.coupon.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 사용 기한이 지난 발급분을 사용하려 할 때 던지는 예외다. */
public class CouponExpiredException extends BaseException {

    public CouponExpiredException(ErrorCode errorCode) {
        super(errorCode);
    }
}
