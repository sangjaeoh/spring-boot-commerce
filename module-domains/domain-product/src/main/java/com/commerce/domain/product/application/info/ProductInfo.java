package com.commerce.domain.product.application.info;

import com.commerce.domain.product.domain.Product;
import com.commerce.domain.product.domain.ProductStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 카탈로그 그룹 조회 경계 모델이다. 판매가는 포함하지 않는다. */
public record ProductInfo(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        @Nullable UUID categoryId,
        Instant createdAt,
        Instant updatedAt) {

    /** 상품 엔티티에서 조회 모델을 만든다. */
    public static ProductInfo from(Product product) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getCategoryId(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
