package com.commerce.domain.payment.domain;

/** 결제 승인 실패 사유다. */
public enum FailureReason {
    /** 잔액 부족. */
    INSUFFICIENT_BALANCE,
    /** 한도 초과. */
    LIMIT_EXCEEDED,
    /** 사용할 수 없는 결제 수단. */
    INVALID_METHOD,
    /** 이상거래 심사 거절. */
    RISK_DECLINED,
    /** PG 오류. 청구가 PG에 도달하지 않은 확정에도 쓴다. */
    GATEWAY_ERROR
}
