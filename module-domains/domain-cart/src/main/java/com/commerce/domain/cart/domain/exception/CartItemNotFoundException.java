package com.commerce.domain.cart.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 장바구니에 해당 변형 라인이 없을 때 던지는 예외다. */
public class CartItemNotFoundException extends BaseException {

    public CartItemNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
