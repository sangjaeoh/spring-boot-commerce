package com.commerce.app.admin.web.v1.admin.category.response;

import com.commerce.domain.product.application.info.CategoryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID") UUID id,
        @Schema(description = "카테고리 이름") String name) {

    /** 카테고리 조회 모델에서 응답을 만든다. */
    public static CategoryResponse from(CategoryInfo category) {
        return new CategoryResponse(category.id(), category.name());
    }
}
