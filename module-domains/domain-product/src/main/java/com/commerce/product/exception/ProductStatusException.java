package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 상품 상태 전이를 시도할 때 던진다. */
public class ProductStatusException extends BaseException {

    public ProductStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
