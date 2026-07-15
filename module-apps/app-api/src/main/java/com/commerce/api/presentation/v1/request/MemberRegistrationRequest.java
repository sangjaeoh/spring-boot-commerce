package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 가입 요청이다.
 *
 * <p>이메일·이름·패스워드는 공백일 수 없다. 이메일·패스워드 형식 검증은 도메인이 소유하므로 여기서는 형식을
 * 검사하지 않는다(형식 오류는 도메인이 400 {@code MEMBER_INVALID_EMAIL_FORMAT}·
 * {@code MEMBER_INVALID_PASSWORD_FORMAT}로 거부).
 */
public record MemberRegistrationRequest(
        @NotBlank String email,
        @NotBlank String name,
        @NotBlank String password) {}
