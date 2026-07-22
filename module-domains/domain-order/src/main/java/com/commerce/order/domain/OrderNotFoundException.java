package com.commerce.order.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 주문을 찾지 못했을 때 던지는 예외다. */
public class OrderNotFoundException extends BaseException {

    public OrderNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
