package com.commerce.api.presentation.v1.response;

import com.commerce.product.entity.ProductStatus;
import com.commerce.product.info.ProductInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 관리자 상품 목록 행 응답이다. 노출 상태(숨김 포함)를 싣되 대표가·품절 파생은 싣지 않는다(카탈로그와 별개). */
public record ProductAdminResponse(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductAdminResponse from(ProductInfo product) {
        return new ProductAdminResponse(
                product.id(),
                product.name(),
                product.description(),
                product.status(),
                product.createdAt(),
                product.updatedAt());
    }
}
