package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 장바구니 라인 수량 변경 요청이다.
 *
 * <p>수량은 1 이상이며, 증량은 담기와 같은 자격 게이트를 받는다. 회원은 토큰 주체에서 도출한다.
 */
public record ChangeCartItemQuantityRequest(
        @NotNull @Positive Integer quantity) {}
