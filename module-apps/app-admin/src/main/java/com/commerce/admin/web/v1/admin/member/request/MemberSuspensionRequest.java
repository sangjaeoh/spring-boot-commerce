package com.commerce.admin.web.v1.admin.member.request;

import com.commerce.member.entity.SuspensionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원 정지 요청")
public record MemberSuspensionRequest(
        @Schema(description = "정지 사유") @NotNull SuspensionReason reason) {}
