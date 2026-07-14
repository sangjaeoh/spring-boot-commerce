package com.commerce.payment.entity;

/** 동기 승인 결제 수단이다. {@code amount > 0}일 때만 존재한다. */
public enum PaymentMethod {
    CARD,
    EASY_PAY,
    BANK_TRANSFER
}
