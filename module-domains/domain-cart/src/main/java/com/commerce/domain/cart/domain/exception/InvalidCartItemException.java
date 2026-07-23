package com.commerce.domain.cart.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 장바구니 라인 수량이 범위를 벗어날 때 던지는 예외다. */
public class InvalidCartItemException extends BaseException {

    public InvalidCartItemException(ErrorCode errorCode) {
        super(errorCode);
    }
}
