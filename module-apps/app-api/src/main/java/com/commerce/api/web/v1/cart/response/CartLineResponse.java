package com.commerce.api.web.v1.cart.response;

import com.commerce.api.facade.CartLineView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 장바구니 라인 응답이다. 변형 현재가·소계와 주문 가능(orderable) 파생을 싣는다. */
public record CartLineResponse(
        UUID variantId, @Nullable String optionLabel, long unitPrice, int quantity, long subtotal, boolean orderable) {

    public static CartLineResponse from(CartLineView line) {
        return new CartLineResponse(
                line.variantId(),
                line.optionLabel(),
                line.unitPrice().amount(),
                line.quantity(),
                line.subtotal().amount(),
                line.orderable());
    }
}
