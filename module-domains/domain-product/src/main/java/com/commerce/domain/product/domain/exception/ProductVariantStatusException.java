package com.commerce.domain.product.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 허용되지 않은 변형 상태 전이(은퇴 변형 변경 포함)를 시도할 때 던지는 예외다. */
public class ProductVariantStatusException extends BaseException {

    public ProductVariantStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}
