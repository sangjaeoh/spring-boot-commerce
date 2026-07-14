package com.commerce.coupon.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 발급 쿠폰을 찾지 못했을 때(미소유 포함) 던진다. */
public class IssuedCouponNotFoundException extends BaseException {

    public IssuedCouponNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
