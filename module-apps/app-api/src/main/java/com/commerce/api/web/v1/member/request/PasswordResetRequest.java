package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 요청이다. 비밀번호 형식을 검증하지 않는다. */
@Schema(description = "비밀번호 재설정 요청")
public record PasswordResetRequest(
        @Schema(description = "재설정 메일로 받은 토큰") @NotBlank String token,
        @Schema(description = "새 비밀번호") @NotBlank String newPassword) {}
