package com.commerce.domain.payment.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 허용되지 않은 결제 상태 전이를 시도할 때 던지는 예외다. */
public class PaymentStatusException extends BaseException {

    public PaymentStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
