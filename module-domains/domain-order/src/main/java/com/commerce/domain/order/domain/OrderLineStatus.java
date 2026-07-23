package com.commerce.domain.order.domain;

/** 주문 라인의 취소 축 상태다. */
public enum OrderLineStatus {
    /** 주문됨 — 초기 상태. */
    ORDERED,
    /** 취소 진행 중 — 환불액이 확정됐고 결제 환불이 끝나면 취소로 완결된다. */
    CANCELLING,
    /** 취소됨. */
    CANCELLED,
    /** 반품 요청됨 — 관리자 승인 대기. */
    RETURN_REQUESTED,
    /** 반품 진행 중 — 환불액이 확정됐고 결제 환불이 끝나면 반품으로 완결된다. */
    RETURNING,
    /** 반품됨. */
    RETURNED
}
