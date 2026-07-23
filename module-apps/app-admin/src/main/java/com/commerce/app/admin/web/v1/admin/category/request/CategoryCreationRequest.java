package com.commerce.app.admin.web.v1.admin.category.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 생성 요청")
public record CategoryCreationRequest(
        @Schema(description = "카테고리 이름") @NotBlank @Size(max = 100)
        String name) {}
