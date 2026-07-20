package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청이다. 이메일 형식을 검증하지 않는다. */
@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "이메일") @NotBlank String email,
        @Schema(description = "비밀번호") @NotBlank String password) {}
