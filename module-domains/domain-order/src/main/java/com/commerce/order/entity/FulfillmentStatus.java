package com.commerce.order.entity;

/** 주문의 물리 이행 축 상태다. */
public enum FulfillmentStatus {
    NOT_STARTED,
    PREPARING,
    ON_HOLD,
    SHIPPED,
    DELIVERED
}
