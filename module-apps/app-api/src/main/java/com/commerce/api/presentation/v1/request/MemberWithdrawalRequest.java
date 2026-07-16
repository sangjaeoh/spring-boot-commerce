package com.commerce.api.presentation.v1.request;

import com.commerce.member.entity.WithdrawalReason;
import jakarta.validation.constraints.NotNull;

/** 회원 탈퇴 요청이다. 사유가 필수다. */
public record MemberWithdrawalRequest(@NotNull WithdrawalReason reason) {}
