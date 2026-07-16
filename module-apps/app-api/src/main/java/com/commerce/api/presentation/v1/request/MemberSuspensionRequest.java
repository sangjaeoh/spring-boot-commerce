package com.commerce.api.presentation.v1.request;

import com.commerce.member.entity.SuspensionReason;
import jakarta.validation.constraints.NotNull;

/** 회원 정지 요청이다. 사유가 필수다. */
public record MemberSuspensionRequest(@NotNull SuspensionReason reason) {}
