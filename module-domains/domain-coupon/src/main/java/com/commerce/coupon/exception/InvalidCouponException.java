package com.commerce.coupon.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 쿠폰 생성 입력(할인 정책·유효 기간·사용 창)이 올바르지 않을 때 던지는 예외다. */
public class InvalidCouponException extends BaseException {

    public InvalidCouponException(ErrorCode errorCode) {
        super(errorCode);
    }
}
