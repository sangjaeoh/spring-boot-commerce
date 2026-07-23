package com.commerce.app.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 리프레시 토큰을 실은 요청이다. 토큰 갱신·로그아웃이 공용한다. */
@Schema(description = "리프레시 토큰 요청")
public record RefreshTokenRequest(
        @Schema(description = "리프레시 토큰") @NotBlank String refreshToken) {}
