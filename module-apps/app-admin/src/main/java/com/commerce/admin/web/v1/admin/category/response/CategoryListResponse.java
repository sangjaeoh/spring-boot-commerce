package com.commerce.admin.web.v1.admin.category.response;

import com.commerce.product.info.CategoryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "카테고리 목록 응답")
public record CategoryListResponse(
        @Schema(description = "활성 카테고리 목록(이름순)") List<CategoryResponse> categories) {

    public CategoryListResponse {
        categories = List.copyOf(categories);
    }

    /** 카테고리 조회 모델 목록에서 응답을 만든다. */
    public static CategoryListResponse from(List<CategoryInfo> categories) {
        return new CategoryListResponse(
                categories.stream().map(CategoryResponse::from).toList());
    }
}
