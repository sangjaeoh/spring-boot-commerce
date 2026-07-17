package com.commerce.api.web.v1.product.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 변형 판매가 변경 요청이다. 가격은 1원 이상이다. */
public record VariantPriceChangeRequest(@NotNull @Positive Long price) {}
