package com.commerce.api.web.v1.order.response;

import com.commerce.order.info.OrderLineInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 라인 응답이다. 단가·옵션은 주문 시점 스냅샷이다. */
@Schema(description = "주문 라인 응답. 단가·옵션은 주문 시점 스냅샷이다.")
public record OrderLineResponse(
        @Schema(description = "변형(SKU) ID") UUID variantId,
        @Schema(description = "상품 ID") UUID productId,
        @Schema(description = "상품명(주문 시점 스냅샷)") String productName,

        @Schema(description = "옵션 표기(주문 시점 스냅샷). 옵션이 없으면 생략", nullable = true) @Nullable
        String optionLabel,

        @Schema(description = "단가(원 단위, 주문 시점 스냅샷)") long unitPrice,
        @Schema(description = "수량") int quantity) {

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
