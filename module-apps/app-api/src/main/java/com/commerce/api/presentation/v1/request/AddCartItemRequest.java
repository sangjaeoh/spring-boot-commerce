package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * 장바구니 담기 요청이다.
 *
 * <p>같은 변형 재담기는 수량을 합산한다. 인증이 범위 밖이라 회원을 요청이 싣는다.
 */
public record AddCartItemRequest(
        @NotNull UUID memberId,
        @NotNull UUID variantId,
        @NotNull @Positive Integer quantity) {}
