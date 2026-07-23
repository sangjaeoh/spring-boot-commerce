package com.commerce.domain.product.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 변형 생성·가격 변경 입력(가격 미달·옵션 형식 오류)이 올바르지 않을 때 던지는 예외다. */
public class InvalidVariantException extends BaseException {

    public InvalidVariantException(ErrorCode errorCode) {
        super(errorCode);
    }
}
