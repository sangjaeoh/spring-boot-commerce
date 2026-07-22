package com.commerce.api.web.v1.admin.inquiry.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 관리자 답변 등록 요청이다. 본문 허용 범위는 도메인이 검증한다. */
@Schema(description = "문의 답변 등록 요청")
public record InquiryAnswerRequest(
        @Schema(description = "답변 본문(1~1000자)") @NotNull String content) {}
