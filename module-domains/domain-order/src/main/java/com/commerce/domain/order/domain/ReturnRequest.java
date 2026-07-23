package com.commerce.domain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 주문 단위 반품 요청 축 값 객체다. 전 컴포넌트가 없으면(null) 반품 요청 이력이 없는 주문이다.
 *
 * @param returnStatus 반품 요청 축 상태. 요청이 없으면 없다
 * @param returnRequestedAt 반품 요청 시각. 요청이 없으면 없다
 * @param returnReason 반품 요청 사유. 요청이 없으면 없다
 */
@Embeddable
record ReturnRequest(
        @Enumerated(EnumType.STRING) @Column(name = "return_status") @Nullable
        ReturnStatus returnStatus,

        @Column(name = "return_requested_at") @Nullable Instant returnRequestedAt,

        @Enumerated(EnumType.STRING) @Column(name = "return_reason") @Nullable
        RefundReason returnReason) {

    /** 반품을 요청 상태로 남긴다. */
    ReturnRequest request(RefundReason reason, Instant now) {
        return new ReturnRequest(ReturnStatus.REQUESTED, now, reason);
    }

    /** 반품 요청을 거절한다. 요청 시각·사유는 그대로 보존한다. */
    ReturnRequest reject() {
        return new ReturnRequest(ReturnStatus.REJECTED, returnRequestedAt, returnReason);
    }

    /** 반품 요청을 완료로 종결한다. 요청 시각·사유는 그대로 보존한다. */
    ReturnRequest complete() {
        return new ReturnRequest(ReturnStatus.COMPLETED, returnRequestedAt, returnReason);
    }
}
