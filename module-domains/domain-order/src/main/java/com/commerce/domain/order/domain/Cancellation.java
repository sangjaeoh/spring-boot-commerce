package com.commerce.domain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 주문 취소 축 값 객체다. 전 컴포넌트가 없으면(null) 취소 이력이 없는 주문이다.
 *
 * @param cancelRequestedAt 취소 개시 마커. 개시되지 않았으면 없다
 * @param cancelledAt 취소 시각. 취소되지 않았으면 없다
 * @param cancellationReason 취소 사유. 취소되지 않았으면 없다
 */
@Embeddable
record Cancellation(
        @Column(name = "cancel_requested_at") @Nullable Instant cancelRequestedAt,
        @Column(name = "cancelled_at") @Nullable Instant cancelledAt,

        @Enumerated(EnumType.STRING) @Column(name = "cancellation_reason") @Nullable
        CancellationReason cancellationReason) {

    /** 취소 개시 마커를 남긴다. 이미 개시됐으면 그대로 반환한다(재개시 no-op). */
    Cancellation request(Instant now) {
        return cancelRequestedAt != null ? this : new Cancellation(now, null, null);
    }

    /** 취소를 완결해 시각·사유를 기록한다. 개시 마커는 있던 그대로 보존한다. */
    Cancellation complete(CancellationReason reason, Instant now) {
        return new Cancellation(cancelRequestedAt, now, reason);
    }
}
