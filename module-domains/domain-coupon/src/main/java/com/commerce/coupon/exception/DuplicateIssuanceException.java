package com.commerce.coupon.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 회원에게 같은 쿠폰을 두 번 발급하려 할 때 던진다. */
public class DuplicateIssuanceException extends BaseException {

    public DuplicateIssuanceException(ErrorCode errorCode) {
        super(errorCode);
    }
}
