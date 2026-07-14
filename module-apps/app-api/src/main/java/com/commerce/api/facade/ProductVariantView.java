package com.commerce.api.facade;

import com.commerce.core.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 상세의 변형별 뷰다. 주문가능은 재고가 SELLABLE이고 수량이 1 이상인 변형이다. */
public record ProductVariantView(UUID variantId, @Nullable String optionLabel, Money price, boolean orderable) {}
