package com.commerce.api.facade;

import com.commerce.core.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 장바구니 라인 뷰다. 변형 현재가와 소계(현재가 × 수량)를 싣는다. */
public record CartLineView(
        UUID variantId, @Nullable String optionLabel, Money unitPrice, int quantity, Money subtotal) {}
