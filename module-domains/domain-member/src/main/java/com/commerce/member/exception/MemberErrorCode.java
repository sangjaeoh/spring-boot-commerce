package com.commerce.member.exception;

import com.commerce.core.exception.ErrorCode;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", "회원을 찾을 수 없다.", 404),
    DUPLICATE_EMAIL("MEMBER_DUPLICATE_EMAIL", "이미 사용 중인 이메일이다.", 409),
    INVALID_EMAIL_FORMAT("MEMBER_INVALID_EMAIL_FORMAT", "이메일 형식이 올바르지 않다.", 400),
    INVALID_STATUS_TRANSITION("MEMBER_INVALID_STATUS_TRANSITION", "허용되지 않은 회원 상태 전이다.", 409);

    private final String code;
    private final String message;
    private final int status;

    MemberErrorCode(String code, String message, int status) {
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
