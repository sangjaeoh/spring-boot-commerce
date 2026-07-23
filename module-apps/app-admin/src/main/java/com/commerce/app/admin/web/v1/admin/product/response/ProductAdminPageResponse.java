package com.commerce.app.admin.web.v1.admin.product.response;

import com.commerce.common.web.paging.PaginationResponse;
import com.commerce.domain.product.application.info.ProductInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "관리자 상품 목록 페이지 응답(숨김 포함)")
public record ProductAdminPageResponse(
        @Schema(description = "상품 목록") List<ProductAdminResponse> products,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public ProductAdminPageResponse {
        products = List.copyOf(products);
    }

    /** 상품 조회 페이지에서 응답을 만든다. */
    public static ProductAdminPageResponse from(Page<ProductInfo> page) {
        return new ProductAdminPageResponse(
                page.getContent().stream().map(ProductAdminResponse::from).toList(), PaginationResponse.from(page));
    }
}
