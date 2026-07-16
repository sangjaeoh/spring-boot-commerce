package com.commerce.api.facade;

import com.commerce.core.money.Money;
import java.util.List;
import java.util.UUID;

/** 장바구니 합성 뷰다. 라인별 변형 현재가·소계·주문 가능 파생과 총액(주문 가능 라인 합)을 싣는다. */
public record CartView(UUID memberId, List<CartLineView> lines, Money totalAmount) {

    public CartView {
        lines = List.copyOf(lines);
    }
}
