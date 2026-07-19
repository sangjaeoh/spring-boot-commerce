package com.commerce.api.web.v1.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "체크아웃 결과")
public record CheckoutResponse(
        @Schema(description = "결제 완료된 주문 ID(문자열)") String orderId) {

    public static CheckoutResponse from(UUID orderId) {
        return new CheckoutResponse(orderId.toString());
    }
}
