package com.commerce.common.core.exception;

/** {@link ErrorCode}를 싣는, 경계에 도달하는 도메인 예외의 최상위 타입이다. */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
