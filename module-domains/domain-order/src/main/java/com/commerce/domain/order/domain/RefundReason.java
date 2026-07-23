package com.commerce.domain.order.domain;

/** 전체 반품 환불 사유다. */
public enum RefundReason {
    /** 단순 변심. */
    CHANGE_OF_MIND,
    /** 상품 하자. */
    PRODUCT_DEFECT,
    /** 오배송. */
    WRONG_DELIVERY,
    /** 상담 수동 처리. */
    CS_MANUAL
}
