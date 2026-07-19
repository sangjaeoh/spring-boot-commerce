package com.commerce.api.web.v1.cart.response;

import com.commerce.api.facade.CartView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "장바구니 응답")
public record CartResponse(
        @Schema(description = "회원 ID") UUID memberId,
        @Schema(description = "장바구니 라인 목록") List<CartLineResponse> lines,
        @Schema(description = "총액(주문 가능 라인 소계 합)") long totalAmount) {

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
