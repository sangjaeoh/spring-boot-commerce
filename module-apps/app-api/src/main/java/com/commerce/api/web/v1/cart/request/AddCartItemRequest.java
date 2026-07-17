package com.commerce.api.web.v1.cart.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * 장바구니 담기 요청이다.
 *
 * <p>같은 변형 재담기는 수량을 합산한다. 담는 회원은 토큰 주체에서 도출한다.
 */
public record AddCartItemRequest(
        @NotNull UUID variantId, @NotNull @Positive Integer quantity) {}
