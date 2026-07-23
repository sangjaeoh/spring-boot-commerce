package com.commerce.app.api.web.v1.inquiry.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 문의 작성 요청이다. 본문 허용 범위는 도메인이 검증한다. */
@Schema(description = "문의 작성 요청")
public record InquiryRequest(
        @Schema(description = "문의 본문(1~1000자)") @NotNull String content,

        @Schema(description = "비밀글 여부 — 비밀글 내용은 작성자·관리자만 열람한다") @NotNull
        Boolean secret) {}
