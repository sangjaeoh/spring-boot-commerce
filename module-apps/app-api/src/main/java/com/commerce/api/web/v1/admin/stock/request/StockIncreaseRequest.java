package com.commerce.api.web.v1.admin.stock.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 재고 재입고 요청이다. 수량은 1 이상이다. */
@Schema(description = "재고 재입고 요청")
public record StockIncreaseRequest(
        @Schema(description = "재입고 수량") @NotNull @Positive Integer quantity) {}
