package com.commerce.api.exception;

import com.commerce.core.exception.ErrorCode;

/**
 * 앱 계층(파사드)이 소유하는 크로스 도메인 정책 위반 코드다.
 *
 * <p>어느 단일 도메인에도 속하지 않는 조율 규칙(주문 자격·주문 가능 합성·쿠폰 적용성·결제 결과·탈퇴
 * 게이트)의 거부를 표현한다. 단일 도메인 불변식 위반은 해당 도메인 {@code ErrorCode}가 소유한다.
 */
public enum ApiErrorCode implements ErrorCode {
    MEMBER_NOT_ELIGIBLE("API_MEMBER_NOT_ELIGIBLE", "주문 자격이 없는 회원이다.", 409),
    EMPTY_CART("API_EMPTY_CART", "장바구니가 비어 있어 주문할 수 없다.", 409),
    NOT_ORDERABLE("API_NOT_ORDERABLE", "주문할 수 없는 상품·변형이 포함돼 있다.", 409),
    INSUFFICIENT_STOCK("API_INSUFFICIENT_STOCK", "재고가 부족하다.", 409),
    COUPON_NOT_APPLICABLE("API_COUPON_NOT_APPLICABLE", "적용할 수 없는 쿠폰이다.", 409),
    PAYMENT_METHOD_REQUIRED("API_PAYMENT_METHOD_REQUIRED", "결제 금액이 있으면 결제 수단이 필요하다.", 400),
    PAYMENT_DECLINED("API_PAYMENT_DECLINED", "결제가 거절됐다.", 402),
    ORDER_NOT_CANCELLABLE("API_ORDER_NOT_CANCELLABLE", "취소할 수 없는 주문 상태다.", 409),
    ORDER_NOT_REFUNDABLE("API_ORDER_NOT_REFUNDABLE", "환불할 수 없는 주문 상태다.", 409),
    WITHDRAWAL_BLOCKED("API_WITHDRAWAL_BLOCKED", "미배송 결제 주문이 있어 탈퇴할 수 없다.", 409),
    WEBHOOK_SIGNATURE_INVALID("API_WEBHOOK_SIGNATURE_INVALID", "웹훅 서명이 유효하지 않다.", 401),
    WEBHOOK_PAYLOAD_INVALID("API_WEBHOOK_PAYLOAD_INVALID", "웹훅 페이로드를 해석할 수 없다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ApiErrorCode(String code, String message, int status) {
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
