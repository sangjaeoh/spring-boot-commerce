package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일·패스워드 형식 검증은 도메인이 소유하므로 여기서는 형식을 검사하지 않는다(형식 오류는 도메인이 400
 * {@code MEMBER_INVALID_EMAIL_FORMAT}·{@code MEMBER_INVALID_PASSWORD_FORMAT}로 거부).
 */
@Schema(description = "회원 가입 요청")
public record MemberRegistrationRequest(
        @Schema(description = "이메일") @NotBlank String email,
        @Schema(description = "이름") @NotBlank String name,
        @Schema(description = "비밀번호") @NotBlank String password) {}
