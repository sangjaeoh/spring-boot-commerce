package com.commerce.api.web.v1.cart.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 회원은 토큰 주체에서 도출한다. */
@Schema(description = "장바구니 라인 수량 변경 요청")
public record ChangeCartItemQuantityRequest(
        @Schema(description = "변경할 수량(1 이상)") @NotNull @Positive
        Integer quantity) {}
