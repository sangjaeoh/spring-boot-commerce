package com.commerce.admin.web.v1.admin.product.response;

import com.commerce.product.application.info.ProductInfo;
import com.commerce.product.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 대표가·품절 파생을 싣지 않는 응답이다. */
@Schema(description = "관리자 상품 목록 행 응답")
public record ProductAdminResponse(
        @Schema(description = "상품 ID") UUID id,
        @Schema(description = "상품명") String name,

        @Schema(description = "상세 설명", nullable = true) @Nullable
        String description,

        @Schema(description = "노출 상태(숨김 포함)") ProductStatus status,

        @Schema(description = "소속 카테고리 ID. 미분류면 없음", nullable = true) @Nullable
        UUID categoryId,

        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

    /** 상품 조회 모델에서 응답을 만든다. */
    public static ProductAdminResponse from(ProductInfo product) {
        return new ProductAdminResponse(
                product.id(),
                product.name(),
                product.description(),
                product.status(),
                product.categoryId(),
                product.createdAt(),
                product.updatedAt());
    }
}
