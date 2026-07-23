package com.commerce.app.api.web.v1.product.response;

import com.commerce.app.api.facade.view.ProductSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "카탈로그 목록의 상품별 응답")
public record ProductSummaryResponse(
        @Schema(description = "상품 ID") UUID id,
        @Schema(description = "상품명") String name,

        @Schema(description = "대표가(원 단위, 변형 최저가)", nullable = true) @Nullable
        Long fromPrice,

        @Schema(description = "품절 여부") boolean soldOut,

        @Schema(description = "대표 이미지 URL. 이미지가 없으면 없음", nullable = true) @Nullable
        String imageUrl) {

    /** 카탈로그 상품 뷰에서 응답을 만든다. */
    public static ProductSummaryResponse from(ProductSummaryView product) {
        return new ProductSummaryResponse(
                product.id(),
                product.name(),
                product.fromPrice() == null ? null : product.fromPrice().amount(),
                product.soldOut(),
                product.imageUrl());
    }
}
