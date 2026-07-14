package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 한 상품에 비-RETIRED 변형과 같은 옵션 조합으로 생성을 시도할 때 던진다. */
public class DuplicateVariantOptionException extends BaseException {

    public DuplicateVariantOptionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
