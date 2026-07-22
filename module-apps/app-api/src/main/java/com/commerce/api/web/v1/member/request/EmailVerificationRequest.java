package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 이메일 소유 인증 요청이다. */
@Schema(description = "이메일 인증 요청")
public record EmailVerificationRequest(
        @Schema(description = "인증 메일로 받은 토큰") @NotBlank String token) {}
