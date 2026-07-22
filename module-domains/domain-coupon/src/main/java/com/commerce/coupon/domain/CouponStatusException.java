package com.commerce.coupon.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 쿠폰·발급분 상태 전이(발급 불가·사용 불가 포함)를 시도할 때 던지는 예외다. */
public class CouponStatusException extends BaseException {

    public CouponStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
