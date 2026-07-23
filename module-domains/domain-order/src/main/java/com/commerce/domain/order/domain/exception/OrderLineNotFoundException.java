package com.commerce.domain.order.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 주문에 대상 라인이 없을 때 던지는 예외다. */
public class OrderLineNotFoundException extends BaseException {

    public OrderLineNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
