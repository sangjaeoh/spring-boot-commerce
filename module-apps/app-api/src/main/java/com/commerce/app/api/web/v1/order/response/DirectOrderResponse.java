package com.commerce.app.api.web.v1.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "바로구매 결과")
public record DirectOrderResponse(
        @Schema(description = "결제 완료된 주문 ID(문자열)") String orderId) {

    /** 결제 완료된 주문 ID에서 응답을 만든다. */
    public static DirectOrderResponse from(UUID orderId) {
        return new DirectOrderResponse(orderId.toString());
    }
}
