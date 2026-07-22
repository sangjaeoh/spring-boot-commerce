package com.commerce.order.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 결제 축 상태 전이(출고 이후 취소 포함)를 시도할 때 던지는 예외다. */
public class OrderStatusException extends BaseException {

    public OrderStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
