package com.commerce.order.entity;

/** 주문의 물리 이행 축 상태다. 결제 축과 직교하되 이행 전진은 결제 완료에서만 유효하다. */
public enum FulfillmentStatus {
    NOT_STARTED,
    PREPARING,
    ON_HOLD,
    SHIPPED,
    DELIVERED
}
