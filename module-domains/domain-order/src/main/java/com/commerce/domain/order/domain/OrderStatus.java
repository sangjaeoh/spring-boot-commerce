package com.commerce.domain.order.domain;

/** 주문의 결제 축 상태다. */
public enum OrderStatus {
    /** 결제 전. 주문 생성 직후 진입한다. */
    PENDING,
    /** 결제 완료. PENDING에서만 진입한다. */
    PAID,
    /** 취소 완료. PENDING·PAID 양쪽에서 진입하는 종료 상태. */
    CANCELLED,
    /** 반품 환불 완료. 이행 DELIVERED인 PAID에서만 진입하는 종료 상태. */
    REFUNDED
}
