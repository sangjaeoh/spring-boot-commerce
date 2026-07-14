package com.commerce.order.info;

import com.commerce.core.money.Money;
import com.commerce.order.entity.OrderLine;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 라인 조회 경계 모델이다. */
public record OrderLineInfo(
        UUID variantId,
        UUID productId,
        String productName,
        @Nullable String optionLabel,
        Money unitPrice,
        int quantity) {

    public static OrderLineInfo from(OrderLine line) {
        return new OrderLineInfo(
                line.getVariantId(),
                line.getProductId(),
                line.getProductName(),
                line.getOptionLabel(),
                line.getUnitPrice(),
                line.getQuantity());
    }
}
