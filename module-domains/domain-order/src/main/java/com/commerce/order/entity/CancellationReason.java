package com.commerce.order.entity;

/** 주문 취소 사유다. */
public enum CancellationReason {
    PAYMENT_FAILED,
    STOCK_SHORTAGE,
    COUPON_CONFLICT,
    CUSTOMER_REQUEST,
    ADMIN_ACTION
}
