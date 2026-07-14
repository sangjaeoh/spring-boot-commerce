package com.commerce.cart.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 장바구니 라인 수량이 범위를 벗어날 때 던진다. */
public class InvalidCartItemException extends BaseException {

    public InvalidCartItemException(ErrorCode errorCode) {
        super(errorCode);
    }
}
