package com.commerce.member.entity;

/** 회원 탈퇴 사유다. 탈퇴분(deletedAt != null)에서만 존재한다. */
public enum WithdrawalReason {
    NO_LONGER_USED,
    PRIVACY_CONCERN,
    DISSATISFIED,
    SWITCHED_SERVICE
}
