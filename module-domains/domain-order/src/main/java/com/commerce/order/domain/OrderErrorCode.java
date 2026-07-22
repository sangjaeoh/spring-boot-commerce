package com.commerce.order.domain;

import com.commerce.core.exception.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "주문을 찾을 수 없다.", 404),
    INVALID_ORDER_STATE_TRANSITION("ORDER_INVALID_STATE_TRANSITION", "허용되지 않은 주문 상태 전이다.", 409),
    CANCEL_NOT_ALLOWED("ORDER_CANCEL_NOT_ALLOWED", "출고 이후 주문은 취소할 수 없다.", 409),
    CANCEL_IN_PROGRESS("ORDER_CANCEL_IN_PROGRESS", "취소 진행 중인 주문은 출고할 수 없다.", 409),
    REFUND_NOT_ALLOWED("ORDER_REFUND_NOT_ALLOWED", "배송 완료된 결제 주문만 환불할 수 있다.", 409),
    RETURN_NOT_ALLOWED("ORDER_RETURN_NOT_ALLOWED", "배송 완료된 결제 주문만 반품을 요청할 수 있다.", 409),
    RETURN_ALREADY_REQUESTED("ORDER_RETURN_ALREADY_REQUESTED", "이미 반품이 요청된 주문이다.", 409),
    RETURN_NOT_REQUESTED("ORDER_RETURN_NOT_REQUESTED", "반품 요청 상태의 주문이 아니다.", 409),
    INVALID_FULFILLMENT_TRANSITION("ORDER_INVALID_FULFILLMENT_TRANSITION", "허용되지 않은 이행 상태 전이다.", 409),
    NOT_PAID("ORDER_NOT_PAID", "결제 완료 주문만 이행할 수 있다.", 409),
    EMPTY_ORDER("ORDER_EMPTY", "주문 라인은 1개 이상이어야 한다.", 400),
    DISCOUNT_EXCEEDS_TOTAL("ORDER_DISCOUNT_EXCEEDS_TOTAL", "할인액이 주문 금액을 초과한다.", 400),
    INVALID_DISCOUNT_COUPON("ORDER_INVALID_DISCOUNT_COUPON", "쿠폰과 할인액의 존재가 일치하지 않는다.", 400);

    private final String code;
    private final String message;
    private final int status;

    OrderErrorCode(String code, String message, int status) {
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
