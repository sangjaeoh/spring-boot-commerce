package com.commerce.api.web.v1.product.response;

import com.commerce.product.info.ProductInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 관리자 상품 목록 페이지 응답이다(숨김 포함). */
@Schema(description = "관리자 상품 목록 페이지 응답(숨김 포함)")
public record ProductAdminPageResponse(
        @Schema(description = "상품 목록") List<ProductAdminResponse> products,
        @Schema(description = "현재 페이지 번호(0부터)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 상품 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

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
