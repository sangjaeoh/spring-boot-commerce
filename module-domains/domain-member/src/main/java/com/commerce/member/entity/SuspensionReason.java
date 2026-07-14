package com.commerce.member.entity;

/** 회원 정지 사유다. {@code SUSPENDED}에서만 존재한다. */
public enum SuspensionReason {
    FRAUD_SUSPECTED,
    PAYMENT_ABUSE,
    POLICY_VIOLATION,
    CS_MANUAL
}
