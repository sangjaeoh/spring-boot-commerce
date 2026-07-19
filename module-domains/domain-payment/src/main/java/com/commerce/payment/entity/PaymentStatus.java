package com.commerce.payment.entity;

/** 결제 상태다. */
public enum PaymentStatus {
    /** 결제 요청됨. 결제 생성 직후 진입한다. */
    REQUESTED,
    /** 승인 완료. 요청 상태에서만 진입한다. */
    APPROVED,
    /** 승인 실패. 요청 상태에서만 진입하는 종료 상태. */
    FAILED,
    /** 취소·환불됨. 승인 상태에서만 진입하는 종료 상태. */
    CANCELLED
}
