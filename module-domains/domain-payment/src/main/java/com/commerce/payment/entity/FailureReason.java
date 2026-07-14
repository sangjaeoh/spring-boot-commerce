package com.commerce.payment.entity;

/** 결제 승인 실패 사유다. */
public enum FailureReason {
    INSUFFICIENT_BALANCE,
    LIMIT_EXCEEDED,
    INVALID_METHOD,
    RISK_DECLINED,
    GATEWAY_ERROR
}
