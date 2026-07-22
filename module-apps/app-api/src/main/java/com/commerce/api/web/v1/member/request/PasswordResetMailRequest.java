package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 메일 요청이다. 이메일 형식을 검증하지 않는다. */
@Schema(description = "비밀번호 재설정 메일 요청")
public record PasswordResetMailRequest(
        @Schema(description = "가입 이메일") @NotBlank String email) {}
