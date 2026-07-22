package com.commerce.api.web.v1.review.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 리뷰 작성·수정 요청이다. 별점·본문 허용 범위는 도메인이 검증한다. */
@Schema(description = "리뷰 작성·수정 요청")
public record ReviewRequest(
        @Schema(description = "별점(1~5)") @NotNull Integer rating,
        @Schema(description = "리뷰 본문(1~1000자)") @NotNull String content) {}
