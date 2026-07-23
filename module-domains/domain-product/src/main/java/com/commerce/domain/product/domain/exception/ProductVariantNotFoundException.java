package com.commerce.domain.product.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 상품 변형을 찾지 못했을 때 던지는 예외다. */
public class ProductVariantNotFoundException extends BaseException {

    public ProductVariantNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
