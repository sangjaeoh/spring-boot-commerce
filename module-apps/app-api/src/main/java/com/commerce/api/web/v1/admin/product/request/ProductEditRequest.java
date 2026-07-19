package com.commerce.api.web.v1.admin.product.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 설명이 null이면 설명을 비운다. */
@Schema(description = "상품명·상세 설명 편집 요청")
public record ProductEditRequest(
        @Schema(description = "상품명") @NotBlank String name,

        @Schema(description = "상세 설명(null이면 설명을 비운다)", nullable = true) @Nullable
        String description) {}
