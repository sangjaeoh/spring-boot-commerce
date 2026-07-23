package com.commerce.domain.member.domain;

/** 회원 탈퇴 사유다. */
public enum WithdrawalReason {
    /** 더 이상 이용하지 않음. */
    NO_LONGER_USED,
    /** 개인정보 우려. */
    PRIVACY_CONCERN,
    /** 서비스 불만족. */
    DISSATISFIED,
    /** 다른 서비스로 이전. */
    SWITCHED_SERVICE
}
