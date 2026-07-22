package com.commerce.order.entity;

/** 주문의 반품 요청 축 상태다. 요청이 없으면 null이다. */
public enum ReturnStatus {
    /** 반품 요청됨. 배송 완료된 결제 주문에서 진입한다. */
    REQUESTED,
    /** 반품 거절. REQUESTED에서 관리자 거절로 진입한다. 재요청할 수 있다. */
    REJECTED,
    /** 반품 완료. REQUESTED 주문이 환불되면 진입하는 종료 상태. */
    COMPLETED
}
