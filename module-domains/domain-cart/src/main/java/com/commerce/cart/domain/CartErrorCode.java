package com.commerce.cart.domain;

import com.commerce.core.exception.ErrorCode;

public enum CartErrorCode implements ErrorCode {
    CART_ITEM_NOT_FOUND("CART_ITEM_NOT_FOUND", "장바구니 라인을 찾을 수 없다.", 404),
    INVALID_QUANTITY("CART_INVALID_QUANTITY", "수량은 1 이상이어야 한다.", 400),
    QUANTITY_LIMIT_EXCEEDED("CART_QUANTITY_LIMIT_EXCEEDED", "수량이 허용 범위를 초과했다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CartErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}
