package com.commerce.cart.application.info;

import com.commerce.cart.domain.Cart;
import java.util.List;
import java.util.UUID;

/** 회원의 장바구니 조회 경계 모델이다. */
public record CartInfo(UUID memberId, List<CartItemInfo> items) {

    public CartInfo {
        items = List.copyOf(items);
    }

    /** 장바구니 엔티티에서 조회 모델을 만든다. */
    public static CartInfo from(Cart cart) {
        return new CartInfo(
                cart.getMemberId(),
                cart.getItems().stream().map(CartItemInfo::from).toList());
    }

    /** 장바구니가 아직 없는 회원의 빈 장바구니를 만든다. */
    public static CartInfo empty(UUID memberId) {
        return new CartInfo(memberId, List.of());
    }
}
