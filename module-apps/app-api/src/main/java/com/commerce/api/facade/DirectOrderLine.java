package com.commerce.api.facade;

import java.util.UUID;

/**
 * 바로구매 요청 라인 값 객체다.
 *
 * @param variantId 주문할 상품 변형 ID
 * @param quantity 주문 수량. 1 이상
 */
public record DirectOrderLine(UUID variantId, int quantity) {

    public DirectOrderLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 한다");
        }
    }
}
