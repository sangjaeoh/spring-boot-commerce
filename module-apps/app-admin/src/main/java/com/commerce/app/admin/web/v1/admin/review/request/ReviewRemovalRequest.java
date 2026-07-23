package com.commerce.app.admin.web.v1.admin.review.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 리뷰 제거 요청")
public record ReviewRemovalRequest(
        @Schema(description = "제거 사유") @NotBlank String reason) {}
