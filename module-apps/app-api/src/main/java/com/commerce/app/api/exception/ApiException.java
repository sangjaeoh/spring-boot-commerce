package com.commerce.app.api.exception;

import com.commerce.common.core.exception.BaseException;

/** 파사드가 크로스 도메인 정책을 거부할 때 던지는 예외다. */
public class ApiException extends BaseException {

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode);
    }
}
