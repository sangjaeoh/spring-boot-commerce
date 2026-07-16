package com.commerce.api.presentation.v1.response;

import com.commerce.product.info.ProductInfo;
import java.util.List;
import org.springframework.data.domain.Page;

/** 관리자 상품 목록 페이지 응답이다(숨김 포함). */
public record ProductAdminPageResponse(
        List<ProductAdminResponse> products, int page, int size, long totalElements, int totalPages) {

    public ProductAdminPageResponse {
        products = List.copyOf(products);
    }

    public static ProductAdminPageResponse from(Page<ProductInfo> page) {
        return new ProductAdminPageResponse(
                page.getContent().stream().map(ProductAdminResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
