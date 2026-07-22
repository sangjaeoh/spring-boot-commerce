package com.commerce.payment.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 이미 결제가 있는 주문에 결제를 요청할 때 던지는 예외다. */
public class DuplicatePaymentException extends BaseException {

    public DuplicatePaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
