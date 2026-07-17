package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductVariantView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 상세의 변형 응답이다. 주문가능은 재고 기준이다. */
public record VariantResponse(UUID variantId, @Nullable String optionLabel, long price, boolean orderable) {

    public static VariantResponse from(ProductVariantView variant) {
        return new VariantResponse(
                variant.variantId(), variant.optionLabel(), variant.price().amount(), variant.orderable());
    }
}
