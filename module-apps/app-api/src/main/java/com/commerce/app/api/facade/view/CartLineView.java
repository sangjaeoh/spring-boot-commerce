package com.commerce.app.api.facade.view;

import com.commerce.domain.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 장바구니 라인 합성 뷰다.
 *
 * @param subtotal 변형 현재가 × 수량.
 * @param orderable 변형 ACTIVE ∧ 상품 ON_SALE·미삭제 ∧ 재고 SELLABLE·수량 1 이상.
 */
public record CartLineView(
        UUID variantId,
        @Nullable String optionLabel,
        Money unitPrice,
        int quantity,
        Money subtotal,
        boolean orderable) {}
