package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 상품 변형을 찾지 못했을 때 던지는 예외다. */
public class ProductVariantNotFoundException extends BaseException {

    public ProductVariantNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
