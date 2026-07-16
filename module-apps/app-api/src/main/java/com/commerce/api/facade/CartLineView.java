package com.commerce.api.facade;

import com.commerce.core.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 장바구니 라인 뷰다. 변형 현재가·소계(현재가 × 수량)와 주문 가능(orderable) 파생을 싣는다. */
public record CartLineView(
        UUID variantId,
        @Nullable String optionLabel,
        Money unitPrice,
        int quantity,
        Money subtotal,
        boolean orderable) {}
