package com.commerce.cart.application;

import com.commerce.cart.application.provided.CartModifier;
import com.commerce.cart.application.required.CartRepository;
import com.commerce.cart.domain.Cart;
import com.commerce.cart.domain.exception.CartErrorCode;
import com.commerce.cart.domain.exception.CartItemNotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CartModifier}의 기본 구현이다. */
@Service
class DefaultCartModifier implements CartModifier {

    private final CartRepository cartRepository;

    DefaultCartModifier(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    @Override
    public void changeItemQuantity(UUID memberId, UUID variantId, int quantity) {
        requireCart(memberId).changeItemQuantity(variantId, quantity);
    }

    @Transactional
    @Override
    public void removeItem(UUID memberId, UUID variantId) {
        requireCart(memberId).removeItem(variantId);
    }

    @Transactional
    @Override
    public void removeItems(UUID memberId, Set<UUID> variantIds) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> cart.removeItems(variantIds));
    }

    @Transactional
    @Override
    public void clear(UUID memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(Cart::clear);
    }

    /** 회원의 장바구니를 찾고 없으면 거부한다. */
    private Cart requireCart(UUID memberId) {
        return cartRepository
                .findByMemberId(memberId)
                .orElseThrow(() -> new CartItemNotFoundException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }
}
