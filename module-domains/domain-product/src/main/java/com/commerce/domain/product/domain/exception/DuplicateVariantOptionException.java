package com.commerce.domain.product.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 한 상품에 비-{@code RETIRED} 변형과 같은 옵션 조합으로 생성을 시도할 때 던지는 예외다. */
public class DuplicateVariantOptionException extends BaseException {

    public DuplicateVariantOptionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
