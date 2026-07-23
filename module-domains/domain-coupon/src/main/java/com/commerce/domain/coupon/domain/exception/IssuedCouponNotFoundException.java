package com.commerce.domain.coupon.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 발급분을 찾지 못했을 때(미소유 포함) 던지는 예외다. */
public class IssuedCouponNotFoundException extends BaseException {

    public IssuedCouponNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
