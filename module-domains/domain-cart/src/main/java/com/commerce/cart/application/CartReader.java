package com.commerce.cart.application;

import com.commerce.cart.application.info.CartInfo;
import com.commerce.cart.application.required.CartRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니 조회를 담당하는 서비스다. */
@Service
public class CartReader {

    private final CartRepository cartRepository;

    public CartReader(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    /** 회원의 장바구니를 조회한다. 없으면 빈 장바구니를 반환한다. */
    @Transactional(readOnly = true)
    public CartInfo getCart(UUID memberId) {
        return cartRepository.findByMemberId(memberId).map(CartInfo::from).orElseGet(() -> CartInfo.empty(memberId));
    }
}
