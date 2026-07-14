package com.commerce.api.presentation.v1.response;

import com.commerce.order.info.OrderLineInfo;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 라인 응답이다. 단가·옵션은 주문 시점 스냅샷이다. */
public record OrderLineResponse(
        UUID variantId,
        UUID productId,
        String productName,
        @Nullable String optionLabel,
        long unitPrice,
        int quantity) {

    public static OrderLineResponse from(OrderLineInfo line) {
        return new OrderLineResponse(
                line.variantId(),
                line.productId(),
                line.productName(),
                line.optionLabel(),
                line.unitPrice().amount(),
                line.quantity());
    }
}
