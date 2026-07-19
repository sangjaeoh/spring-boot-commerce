package com.commerce.api.web.v1.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 이메일은 불변이라 받지 않는다. */
@Schema(description = "회원 표시 이름 변경 요청")
public record MemberRenameRequest(
        @Schema(description = "새 표시 이름") @NotBlank String name) {}
