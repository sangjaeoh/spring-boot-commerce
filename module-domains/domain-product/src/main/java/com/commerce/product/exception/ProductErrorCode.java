package com.commerce.product.exception;

import com.commerce.core.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "상품을 찾을 수 없다.", 404),
    INVALID_PRODUCT_STATE_TRANSITION("PRODUCT_INVALID_STATE_TRANSITION", "허용되지 않은 상품 상태 전이다.", 409),
    VARIANT_NOT_FOUND("PRODUCT_VARIANT_NOT_FOUND", "상품 변형을 찾을 수 없다.", 404),
    INVALID_VARIANT_STATE_TRANSITION("PRODUCT_VARIANT_INVALID_STATE_TRANSITION", "허용되지 않은 변형 상태 전이다.", 409),
    DUPLICATE_VARIANT_OPTION("PRODUCT_VARIANT_DUPLICATE_OPTION", "이미 존재하는 옵션 조합이다.", 409),
    INVALID_PRICE("PRODUCT_VARIANT_INVALID_PRICE", "변형 가격은 1 이상이어야 한다.", 400),
    INVALID_OPTION("PRODUCT_VARIANT_INVALID_OPTION", "옵션 입력이 올바르지 않다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ProductErrorCode(String code, String message, int status) {
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
