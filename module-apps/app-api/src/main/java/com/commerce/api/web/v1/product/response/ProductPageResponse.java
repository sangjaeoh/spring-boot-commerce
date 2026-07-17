package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 카탈로그 상품 목록 페이지 응답이다. */
@Schema(description = "카탈로그 상품 목록 페이지 응답")
public record ProductPageResponse(
        @Schema(description = "상품 목록") List<ProductSummaryResponse> products,
        @Schema(description = "현재 페이지 번호(0부터)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 상품 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

    public ProductPageResponse {
        products = List.copyOf(products);
    }

    public static ProductPageResponse from(Page<ProductSummaryView> page) {
        return new ProductPageResponse(
                page.getContent().stream().map(ProductSummaryResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
