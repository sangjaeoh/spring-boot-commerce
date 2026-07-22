package com.commerce.payment.domain;

import com.commerce.core.exception.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "결제를 찾을 수 없다.", 404),
    DUPLICATE_PAYMENT("PAYMENT_DUPLICATE", "이미 결제가 존재하는 주문이다.", 409),
    INVALID_PAYMENT_STATE_TRANSITION("PAYMENT_INVALID_STATE_TRANSITION", "허용되지 않은 결제 상태 전이다.", 409);

    private final String code;
    private final String message;
    private final int status;

    PaymentErrorCode(String code, String message, int status) {
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
