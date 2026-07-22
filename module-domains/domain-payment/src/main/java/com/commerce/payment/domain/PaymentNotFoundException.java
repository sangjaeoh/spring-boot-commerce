package com.commerce.payment.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 결제를 찾지 못했을 때 던지는 예외다. */
public class PaymentNotFoundException extends BaseException {

    public PaymentNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
