package com.commerce.product.info;

import com.commerce.product.entity.Product;
import com.commerce.product.entity.ProductStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 카탈로그 그룹 조회 경계 모델이다. 판매가는 포함하지 않는다. */
public record ProductInfo(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductInfo from(Product product) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
