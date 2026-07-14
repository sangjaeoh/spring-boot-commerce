package com.commerce.cart.service;

import com.commerce.cart.entity.Cart;
import com.commerce.cart.repository.CartRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니 담기를 담당한다. */
@Service
public class CartAppender {

    private final CartRepository cartRepository;

    public CartAppender(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    /** 회원 장바구니가 없으면 만들고 변형을 담는다. 같은 변형은 수량을 합산한다. */
    @Transactional
    public void addItem(UUID memberId, UUID variantId, int quantity) {
        // 동시 최초 담기의 get-or-create 경합 방어는 범위 밖이다(더블서밋 방어는 common-web 멱등 필터 도입 시).
        Cart cart = cartRepository.findByMemberId(memberId).orElseGet(() -> cartRepository.save(Cart.create(memberId)));
        cart.addItem(variantId, quantity);
    }
}
