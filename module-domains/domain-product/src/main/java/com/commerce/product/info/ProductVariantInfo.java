package com.commerce.product.info;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductVariant;
import com.commerce.product.entity.ProductVariantStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 변형 조회 경계 모델이다. */
public record ProductVariantInfo(
        UUID id,
        UUID productId,
        Money price,
        ProductVariantStatus status,
        String optionSignature,
        @Nullable String optionLabel,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductVariantInfo from(ProductVariant variant) {
        return new ProductVariantInfo(
                variant.getId(),
                variant.getProductId(),
                variant.getPrice(),
                variant.getStatus(),
                variant.getOptionSignature(),
                variant.getOptionLabel(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}
