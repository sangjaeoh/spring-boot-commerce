package com.commerce.api.web.v1.member.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청이다.
 *
 * <p>이메일 형식·자격증명 검증은 회원 도메인이 소유한다. 어떤 실패든 401 {@code MEMBER_INVALID_CREDENTIALS}로
 * 동일하게 거부된다(계정 존재 노출 방지).
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
