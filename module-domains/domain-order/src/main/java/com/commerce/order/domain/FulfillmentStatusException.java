package com.commerce.order.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 이행 축 전이(결제 미완료 이행 포함)를 시도할 때 던지는 예외다. */
public class FulfillmentStatusException extends BaseException {

    public FulfillmentStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
