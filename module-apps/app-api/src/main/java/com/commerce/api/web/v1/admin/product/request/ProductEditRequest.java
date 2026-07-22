package com.commerce.api.web.v1.admin.product.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "상품명·상세 설명·분류 편집 요청")
public record ProductEditRequest(
        @Schema(description = "상품명") @NotBlank String name,

        @Schema(description = "상세 설명(null이면 설명을 비운다)", nullable = true) @Nullable
        String description,

        @Schema(description = "소속 카테고리 ID(null이면 미분류로 해제)", nullable = true) @Nullable
        UUID categoryId) {}
