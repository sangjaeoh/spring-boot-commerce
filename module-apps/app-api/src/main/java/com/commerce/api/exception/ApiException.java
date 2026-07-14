package com.commerce.api.exception;

import com.commerce.core.exception.BaseException;

/**
 * 파사드가 크로스 도메인 정책을 거부할 때 던진다.
 *
 * <p>실린 {@link ApiErrorCode}가 코드·메시지·HTTP 상태를 결정하며 ProblemDetail 핸들러가 소비한다.
 */
public class ApiException extends BaseException {

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode);
    }
}
