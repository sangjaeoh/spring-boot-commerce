package com.commerce.order.domain;

/** 주문의 물리 이행(fulfillment) 축 상태다. */
public enum FulfillmentStatus {
    /** 이행 미개시. 주문 생성 직후 진입한다. */
    NOT_STARTED,
    /** 출고 준비. 결제 완료가 진입시킨다. */
    PREPARING,
    /** 이행 보류. PREPARING에서만 진입한다. */
    ON_HOLD,
    /** 출고 완료. PREPARING에서 출고하면 진입한다. */
    SHIPPED,
    /** 배송 완료. SHIPPED에서만 진입한다. */
    DELIVERED
}
