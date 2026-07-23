package com.commerce.app.admin.web.v1.admin.category.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "카테고리 생성 결과")
public record CategoryCreationResponse(
        @Schema(description = "생성된 카테고리 ID") String categoryId) {

    /** 생성된 카테고리 ID에서 응답을 만든다. */
    public static CategoryCreationResponse from(UUID categoryId) {
        return new CategoryCreationResponse(categoryId.toString());
    }
}
