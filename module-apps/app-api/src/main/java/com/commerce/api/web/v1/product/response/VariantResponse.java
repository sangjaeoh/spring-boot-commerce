package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductVariantView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 상세의 변형 응답이다. 주문가능은 재고 기준이다. */
@Schema(description = "상품 상세의 변형 응답")
public record VariantResponse(
        @Schema(description = "변형 ID") UUID variantId,

        @Schema(description = "옵션 라벨", nullable = true) @Nullable
        String optionLabel,

        @Schema(description = "판매가(원 단위)") long price,
        @Schema(description = "주문 가능 여부(재고 기준)") boolean orderable) {

    public static VariantResponse from(ProductVariantView variant) {
        return new VariantResponse(
                variant.variantId(), variant.optionLabel(), variant.price().amount(), variant.orderable());
    }
}
