package com.commerce.api.web.v1.admin.product.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 변형 판매가 변경 요청이다. 가격은 1원 이상이다. */
@Schema(description = "변형 판매가 변경 요청")
public record VariantPriceChangeRequest(
        @Schema(description = "판매가(원 단위, 1 이상)") @NotNull @Positive
        Long price) {}
