package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductView;
import com.commerce.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "상품 상세 응답")
public record ProductDetailResponse(
        @Schema(description = "상품 ID") UUID id,
        @Schema(description = "상품명") String name,

        @Schema(description = "상세 설명", nullable = true) @Nullable
        String description,

        @Schema(description = "노출 상태") ProductStatus status,

        @Schema(description = "대표가(원 단위, 변형 최저가)", nullable = true) @Nullable
        Long fromPrice,

        @Schema(description = "품절 여부") boolean soldOut,
        @Schema(description = "ACTIVE 변형 목록") List<VariantResponse> variants) {

    public ProductDetailResponse {
        variants = List.copyOf(variants);
    }

    /** 상품 상세 뷰에서 응답을 만든다. */
    public static ProductDetailResponse from(ProductView product) {
        return new ProductDetailResponse(
                product.id(),
                product.name(),
                product.description(),
                product.status(),
                product.fromPrice() == null ? null : product.fromPrice().amount(),
                product.soldOut(),
                product.variants().stream().map(VariantResponse::from).toList());
    }
}
