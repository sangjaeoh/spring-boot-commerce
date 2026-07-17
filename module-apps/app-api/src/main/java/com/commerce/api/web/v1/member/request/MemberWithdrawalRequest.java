package com.commerce.api.web.v1.member.request;

import com.commerce.member.entity.WithdrawalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 회원 탈퇴 요청이다. 사유가 필수다. */
@Schema(description = "회원 탈퇴 요청")
public record MemberWithdrawalRequest(
        @Schema(description = "탈퇴 사유") @NotNull WithdrawalReason reason) {}
