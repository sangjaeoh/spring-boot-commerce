package com.commerce.admin.exception;

import com.commerce.core.exception.ErrorCode;

/** 어드민 앱 계층(파사드)이 소유하는 크로스 도메인 정책 위반 코드다. */
public enum AdminErrorCode implements ErrorCode {
    // 코드 문자열은 클라이언트에 노출된 와이어 계약이라 app-api에서 이전한 값을 유지한다.
    ORDER_NOT_REFUNDABLE("API_ORDER_NOT_REFUNDABLE", "환불할 수 없는 주문 상태다.", 409),
    ORDER_RETURN_NOT_REQUESTED("API_ORDER_RETURN_NOT_REQUESTED", "반품 요청 상태의 주문이 아니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    AdminErrorCode(String code, String message, int status) {
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
