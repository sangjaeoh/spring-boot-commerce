package com.commerce.payment.port;

import com.commerce.payment.entity.FailureReason;
import org.jspecify.annotations.Nullable;

/** PG 승인 결과다. 성공이면 거래 ID를, 실패면 사유를 담는다. */
public record PaymentApproval(
        boolean approved,
        @Nullable String pgTransactionId,
        @Nullable FailureReason failureReason) {

    public PaymentApproval {
        boolean hasTransactionId = pgTransactionId != null;
        boolean hasFailureReason = failureReason != null;
        if (approved != hasTransactionId || approved == hasFailureReason) {
            throw new IllegalArgumentException("승인이면 거래 ID만, 거절이면 사유만 있어야 한다");
        }
    }

    public static PaymentApproval approved(String pgTransactionId) {
        return new PaymentApproval(true, pgTransactionId, null);
    }

    public static PaymentApproval declined(FailureReason failureReason) {
        return new PaymentApproval(false, null, failureReason);
    }
}
