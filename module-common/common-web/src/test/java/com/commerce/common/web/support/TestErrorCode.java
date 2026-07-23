package com.commerce.common.web.support;

import com.commerce.common.core.exception.ErrorCode;

/** 핸들러 검증용 테스트 에러 코드다. 422 상태로 {@link ErrorCode} 계약을 태운다. */
public enum TestErrorCode implements ErrorCode {
    SAMPLE("TEST_SAMPLE", "샘플 도메인 오류다.", 422);

    private final String code;
    private final String message;
    private final int status;

    TestErrorCode(String code, String message, int status) {
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
