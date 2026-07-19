package com.commerce.payment.entity;

/** 동기 승인 결제 수단이다. */
public enum PaymentMethod {
    /** 신용·체크카드. */
    CARD,
    /** 간편결제. */
    EASY_PAY,
    /** 계좌이체. */
    BANK_TRANSFER
}
