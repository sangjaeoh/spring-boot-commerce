package com.commerce.app.batch.exception;

import com.commerce.common.core.exception.ErrorCode;

/** 배치 앱(웹훅 수신)이 소유하는 정책 위반 코드다. */
public enum BatchErrorCode implements ErrorCode {
    // 코드 문자열은 PG에 노출된 와이어 계약이라 app-api에서 이전한 값을 유지한다.
    WEBHOOK_SIGNATURE_INVALID("API_WEBHOOK_SIGNATURE_INVALID", "웹훅 서명이 유효하지 않다.", 401),
    WEBHOOK_PAYLOAD_INVALID("API_WEBHOOK_PAYLOAD_INVALID", "웹훅 페이로드를 해석할 수 없다.", 400);

    private final String code;
    private final String message;
    private final int status;

    BatchErrorCode(String code, String message, int status) {
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
