package com.commerce.cart.application;

import com.commerce.cart.application.info.CartInfo;
import com.commerce.cart.application.provided.CartReader;
import com.commerce.cart.application.required.CartRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CartReader}의 기본 구현이다. */
@Service
class DefaultCartReader implements CartReader {

    private final CartRepository cartRepository;

    DefaultCartReader(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public CartInfo getCart(UUID memberId) {
        return cartRepository.findByMemberId(memberId).map(CartInfo::from).orElseGet(() -> CartInfo.empty(memberId));
    }
}
