package com.commerce.api.web.v1.order.response;

import java.util.UUID;

/** 체크아웃 결과다. 결제 완료된 주문 ID를 문자열로 싣는다. */
public record CheckoutResponse(String orderId) {

    /** 주문 ID를 문자열로 담은 응답을 만든다. */
    public static CheckoutResponse from(UUID orderId) {
        return new CheckoutResponse(orderId.toString());
    }
}
