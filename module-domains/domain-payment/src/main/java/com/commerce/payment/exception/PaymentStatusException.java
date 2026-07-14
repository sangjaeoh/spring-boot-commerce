package com.commerce.payment.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 결제 상태 전이를 시도할 때 던진다. */
public class PaymentStatusException extends BaseException {

    public PaymentStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
