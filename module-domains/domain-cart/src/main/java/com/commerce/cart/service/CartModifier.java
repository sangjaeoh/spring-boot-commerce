package com.commerce.cart.service;

import com.commerce.cart.entity.Cart;
import com.commerce.cart.exception.CartErrorCode;
import com.commerce.cart.exception.CartItemNotFoundException;
import com.commerce.cart.exception.InvalidCartItemException;
import com.commerce.cart.repository.CartRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니 라인 수량 변경·제거를 담당한다. */
@Service
public class CartModifier {

    private final CartRepository cartRepository;

    public CartModifier(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    /**
     * 라인 수량을 바꾼다.
     *
     * @throws CartItemNotFoundException 장바구니나 라인이 없으면
     * @throws InvalidCartItemException 수량이 1 미만이면
     */
    @Transactional
    public void changeItemQuantity(UUID memberId, UUID variantId, int quantity) {
        requireCart(memberId).changeItemQuantity(variantId, quantity);
    }

    /**
     * 라인을 제거한다.
     *
     * @throws CartItemNotFoundException 장바구니나 라인이 없으면
     */
    @Transactional
    public void removeItem(UUID memberId, UUID variantId) {
        requireCart(memberId).removeItem(variantId);
    }

    /** 주어진 변형 라인들을 제거한다. 장바구니가 없으면 무시한다. */
    @Transactional
    public void removeItems(UUID memberId, Set<UUID> variantIds) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> cart.removeItems(variantIds));
    }

    /** 장바구니를 비운다. 장바구니가 없으면 무시한다. */
    @Transactional
    public void clear(UUID memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(Cart::clear);
    }

    private Cart requireCart(UUID memberId) {
        return cartRepository
                .findByMemberId(memberId)
                .orElseThrow(() -> new CartItemNotFoundException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }
}
