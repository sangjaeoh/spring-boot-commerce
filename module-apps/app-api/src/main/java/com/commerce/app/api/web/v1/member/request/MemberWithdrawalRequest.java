package com.commerce.app.api.web.v1.member.request;

import com.commerce.domain.member.domain.WithdrawalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원 탈퇴 요청")
public record MemberWithdrawalRequest(
        @Schema(description = "탈퇴 사유") @NotNull WithdrawalReason reason) {}
