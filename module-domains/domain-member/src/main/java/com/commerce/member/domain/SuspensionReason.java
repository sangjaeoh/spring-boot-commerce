package com.commerce.member.domain;

/** 회원 정지 사유다. */
public enum SuspensionReason {
    /** 부정 거래 의심. */
    FRAUD_SUSPECTED,
    /** 결제 남용. */
    PAYMENT_ABUSE,
    /** 이용 정책 위반. */
    POLICY_VIOLATION,
    /** 고객센터 수동 조치. */
    CS_MANUAL
}
