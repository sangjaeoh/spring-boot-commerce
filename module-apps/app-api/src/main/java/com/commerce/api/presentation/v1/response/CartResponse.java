package com.commerce.api.presentation.v1.response;

import com.commerce.api.facade.CartView;
import java.util.List;
import java.util.UUID;

/** 장바구니 응답이다. 라인별 변형 현재가·소계와 총액을 싣는다. */
public record CartResponse(UUID memberId, List<CartLineResponse> lines, long totalAmount) {

    public CartResponse {
        lines = List.copyOf(lines);
    }

    public static CartResponse from(CartView cart) {
        return new CartResponse(
                cart.memberId(),
                cart.lines().stream().map(CartLineResponse::from).toList(),
                cart.totalAmount().amount());
    }
}
