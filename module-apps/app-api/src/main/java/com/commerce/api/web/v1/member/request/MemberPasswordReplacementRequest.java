package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 본인 비밀번호 변경 요청이다. 비밀번호 형식을 검증하지 않는다. */
@Schema(description = "본인 비밀번호 변경 요청")
public record MemberPasswordReplacementRequest(
        @Schema(description = "현재 비밀번호") @NotBlank String currentPassword,
        @Schema(description = "새 비밀번호") @NotBlank String newPassword) {}
