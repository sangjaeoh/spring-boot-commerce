package com.commerce.product.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 이미지 업로드 입력(형식·크기)이 올바르지 않을 때 던지는 예외다. */
public class InvalidProductImageException extends BaseException {

    public InvalidProductImageException(ErrorCode errorCode) {
        super(errorCode);
    }
}
