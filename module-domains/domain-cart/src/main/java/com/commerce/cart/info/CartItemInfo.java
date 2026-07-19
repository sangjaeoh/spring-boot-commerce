package com.commerce.cart.info;

import com.commerce.cart.entity.CartItem;
import java.util.UUID;

/** 장바구니 라인 조회 경계 모델이다. */
public record CartItemInfo(UUID variantId, int quantity) {

    /** 장바구니 라인 엔티티에서 조회 모델을 만든다. */
    public static CartItemInfo from(CartItem item) {
        return new CartItemInfo(item.getVariantId(), item.getQuantity());
    }
}
