package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductSummaryView;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "카탈로그 상품 목록 페이지 응답")
public record ProductPageResponse(
        @Schema(description = "상품 목록") List<ProductSummaryResponse> products,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public ProductPageResponse {
        products = List.copyOf(products);
    }

    public static ProductPageResponse from(Page<ProductSummaryView> page) {
        return new ProductPageResponse(
                page.getContent().stream().map(ProductSummaryResponse::from).toList(), PaginationResponse.from(page));
    }
}
