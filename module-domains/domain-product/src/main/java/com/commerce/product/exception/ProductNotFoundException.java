package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 활성 상품을 찾지 못했을 때 던진다. */
public class ProductNotFoundException extends BaseException {

    public ProductNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
