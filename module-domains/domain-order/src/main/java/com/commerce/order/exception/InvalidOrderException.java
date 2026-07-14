package com.commerce.order.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 주문 생성 입력(빈 주문·할인 초과·쿠폰/할인 불일치)이 올바르지 않을 때 던진다. */
public class InvalidOrderException extends BaseException {

    public InvalidOrderException(ErrorCode errorCode) {
        super(errorCode);
    }
}
