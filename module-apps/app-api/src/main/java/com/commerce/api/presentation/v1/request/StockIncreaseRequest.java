package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 재고 재입고 요청이다. 수량은 1 이상이다. */
public record StockIncreaseRequest(@NotNull @Positive Integer quantity) {}
