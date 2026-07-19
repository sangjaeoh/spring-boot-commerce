package com.commerce.order.entity;

/** 이행 보류 사유다. */
public enum HoldReason {
    FRAUD_REVIEW,
    ADDRESS_VERIFICATION,
    STOCK_DELAY
}
