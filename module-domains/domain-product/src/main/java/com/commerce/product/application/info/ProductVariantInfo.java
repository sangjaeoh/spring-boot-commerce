package com.commerce.product.application.info;

import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.ProductVariantStatus;
import com.commerce.shared.entity.Money;
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

    /** 변형 엔티티에서 조회 모델을 만든다. */
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
