package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductSummaryView;
import java.util.List;
import org.springframework.data.domain.Page;

/** 카탈로그 상품 목록 페이지 응답이다. */
public record ProductPageResponse(
        List<ProductSummaryResponse> products, int page, int size, long totalElements, int totalPages) {

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
