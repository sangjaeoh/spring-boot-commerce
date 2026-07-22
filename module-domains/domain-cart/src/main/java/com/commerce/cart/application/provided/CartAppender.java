package com.commerce.cart.application.provided;

import com.commerce.cart.domain.InvalidCartItemException;
import java.util.UUID;

/** 장바구니 담기를 담당하는 서비스다. */
public interface CartAppender {

    /**
     * 회원 장바구니가 없으면 만들고 변형을 담는다. 같은 변형은 수량을 합산한다.
     *
     * @throws InvalidCartItemException 수량이 1 미만이거나 합산 수량이 한도를 넘으면
     */
    void addItem(UUID memberId, UUID variantId, int quantity);
}
