package com.commerce.product.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 활성 카테고리를 찾지 못했을 때 던지는 예외다. */
public class CategoryNotFoundException extends BaseException {

    public CategoryNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
