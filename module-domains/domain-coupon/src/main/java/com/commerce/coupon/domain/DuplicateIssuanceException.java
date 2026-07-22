package com.commerce.coupon.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 회원에게 같은 쿠폰을 두 번 발급하려 할 때 던지는 예외다. */
public class DuplicateIssuanceException extends BaseException {

    public DuplicateIssuanceException(ErrorCode errorCode) {
        super(errorCode);
    }
}
