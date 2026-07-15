package com.commerce.web.exception;

import com.commerce.core.exception.ErrorCode;

public enum WebErrorCode implements ErrorCode {
    UNAUTHENTICATED("UNAUTHENTICATED", "인증이 필요하다.", 401),
    FORBIDDEN("FORBIDDEN", "권한이 없다.", 403);

    private final String code;
    private final String message;
    private final int status;

    WebErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}
