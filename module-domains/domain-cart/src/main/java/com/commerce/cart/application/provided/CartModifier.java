package com.commerce.cart.application.provided;

import com.commerce.cart.domain.CartItemNotFoundException;
import com.commerce.cart.domain.InvalidCartItemException;
import java.util.Set;
import java.util.UUID;

/** 장바구니 라인 수량 변경·제거를 담당하는 서비스다. */
public interface CartModifier {

    /**
     * 라인 수량을 바꾼다.
     *
     * @throws CartItemNotFoundException 장바구니나 라인이 없으면
     * @throws InvalidCartItemException 수량이 1 미만이면
     */
    void changeItemQuantity(UUID memberId, UUID variantId, int quantity);

    /**
     * 라인을 제거한다.
     *
     * @throws CartItemNotFoundException 장바구니나 라인이 없으면
     */
    void removeItem(UUID memberId, UUID variantId);

    /** 주어진 변형 라인들을 제거한다. 장바구니가 없으면 무시한다. */
    void removeItems(UUID memberId, Set<UUID> variantIds);

    /** 장바구니를 비운다. 장바구니가 없으면 무시한다. */
    void clear(UUID memberId);
}
