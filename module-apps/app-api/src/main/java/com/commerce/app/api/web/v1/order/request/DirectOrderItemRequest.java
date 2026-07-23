package com.commerce.app.api.web.v1.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** 바로구매 주문 라인이다. */
@Schema(description = "바로구매 주문 라인")
public record DirectOrderItemRequest(
        @Schema(description = "주문할 상품 변형 ID") @NotNull UUID variantId,

        @Schema(description = "주문 수량(1 이상)") @NotNull @Positive
        Integer quantity) {}
