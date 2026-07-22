package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 상품 이미지를 찾지 못했을 때 던지는 예외다. */
public class ProductImageNotFoundException extends BaseException {

    public ProductImageNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
