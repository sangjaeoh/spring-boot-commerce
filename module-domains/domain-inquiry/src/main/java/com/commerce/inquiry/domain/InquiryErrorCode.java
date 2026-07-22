package com.commerce.inquiry.domain;

import com.commerce.core.exception.ErrorCode;

public enum InquiryErrorCode implements ErrorCode {
    INQUIRY_NOT_FOUND("INQUIRY_NOT_FOUND", "문의를 찾을 수 없다.", 404),
    INVALID_CONTENT("INQUIRY_INVALID_CONTENT", "본문은 1~1000자여야 한다.", 400);

    private final String code;
    private final String message;
    private final int status;

    InquiryErrorCode(String code, String message, int status) {
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
