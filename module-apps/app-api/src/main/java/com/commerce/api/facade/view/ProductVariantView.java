package com.commerce.api.facade.view;

import com.commerce.core.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 상세의 변형별 뷰다.
 *
 * @param orderable 재고 SELLABLE ∧ 수량 1 이상.
 */
public record ProductVariantView(UUID variantId, @Nullable String optionLabel, Money price, boolean orderable) {}
