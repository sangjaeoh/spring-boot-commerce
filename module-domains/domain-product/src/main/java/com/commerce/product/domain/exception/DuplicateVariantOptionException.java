package com.commerce.product.domain.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 한 상품에 비-{@code RETIRED} 변형과 같은 옵션 조합으로 생성을 시도할 때 던지는 예외다. */
public class DuplicateVariantOptionException extends BaseException {

    public DuplicateVariantOptionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
