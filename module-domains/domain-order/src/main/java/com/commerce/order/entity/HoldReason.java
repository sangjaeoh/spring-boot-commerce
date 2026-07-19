package com.commerce.order.entity;

/** 이행 보류(hold) 사유다. */
public enum HoldReason {
    /** 이상거래 심사. */
    FRAUD_REVIEW,
    /** 배송지 확인. */
    ADDRESS_VERIFICATION,
    /** 재고 수급 지연. */
    STOCK_DELAY
}
