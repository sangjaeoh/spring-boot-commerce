package com.commerce.api.web.v1.cart.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * 장바구니 담기 요청이다.
 *
 * <p>같은 변형 재담기는 수량을 합산한다. 담는 회원은 토큰 주체에서 도출한다.
 */
@Schema(description = "장바구니 담기 요청")
public record AddCartItemRequest(
        @Schema(description = "담을 상품 변형 ID") @NotNull UUID variantId,

        @Schema(description = "담을 수량(1 이상)") @NotNull @Positive
        Integer quantity) {}
