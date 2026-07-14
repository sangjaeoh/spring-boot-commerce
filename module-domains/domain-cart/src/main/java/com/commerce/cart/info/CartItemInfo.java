package com.commerce.cart.info;

import com.commerce.cart.entity.CartItem;
import java.util.UUID;

/** 장바구니 라인 조회 경계 모델이다. */
public record CartItemInfo(UUID variantId, int quantity) {

    public static CartItemInfo from(CartItem item) {
        return new CartItemInfo(item.getVariantId(), item.getQuantity());
    }
}
