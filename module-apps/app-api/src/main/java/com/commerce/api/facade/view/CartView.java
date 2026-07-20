package com.commerce.api.facade.view;

import com.commerce.core.money.Money;
import java.util.List;
import java.util.UUID;

/**
 * 장바구니 합성 뷰다.
 *
 * @param lines 변형 ID 오름차순 라인 뷰.
 * @param totalAmount 주문가능 라인 소계의 합.
 */
public record CartView(UUID memberId, List<CartLineView> lines, Money totalAmount) {

    public CartView {
        lines = List.copyOf(lines);
    }
}
