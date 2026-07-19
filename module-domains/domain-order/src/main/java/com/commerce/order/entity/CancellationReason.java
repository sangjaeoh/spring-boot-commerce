package com.commerce.order.entity;

/** 주문 취소 사유다. */
public enum CancellationReason {
    /** 결제 실패. */
    PAYMENT_FAILED,
    /** 재고 부족. */
    STOCK_SHORTAGE,
    /** 쿠폰 적용 충돌. */
    COUPON_CONFLICT,
    /** 고객 요청. */
    CUSTOMER_REQUEST,
    /** 관리자 조치. */
    ADMIN_ACTION,
    /** 체크아웃 미완료 이탈. 잔존 PENDING 주문을 스윕이 정리할 때 쓴다. */
    CHECKOUT_ABANDONED
}
