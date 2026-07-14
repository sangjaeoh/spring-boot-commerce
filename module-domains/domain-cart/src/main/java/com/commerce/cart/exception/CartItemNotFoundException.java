package com.commerce.cart.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 장바구니에 해당 변형 라인이 없을 때 던진다. */
public class CartItemNotFoundException extends BaseException {

    public CartItemNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
