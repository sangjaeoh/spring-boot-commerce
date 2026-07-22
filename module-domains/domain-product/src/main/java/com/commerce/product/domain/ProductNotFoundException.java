package com.commerce.product.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 활성 상품을 찾지 못했을 때 던지는 예외다. */
public class ProductNotFoundException extends BaseException {

    public ProductNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
