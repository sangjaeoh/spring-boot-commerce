package com.commerce.order.entity;

/** 주문의 물리 이행(fulfillment) 축 상태다. */
public enum FulfillmentStatus {
    /** 이행 미개시. 주문 생성 직후 진입한다. */
    NOT_STARTED,
    /** 출고 준비. 결제 완료가 진입시킨다. */
    PREPARING,
    /** 이행 보류. PREPARING에서만 진입하고 해제하면 PREPARING으로 돌아간다. */
    ON_HOLD,
    /** 출고 완료. 이후 취소가 막힌다. */
    SHIPPED,
    /** 배송 완료. 반품 환불의 선행 조건. */
    DELIVERED
}
