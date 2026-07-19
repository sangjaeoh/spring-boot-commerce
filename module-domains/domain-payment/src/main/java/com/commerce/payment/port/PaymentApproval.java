package com.commerce.payment.port;

import com.commerce.payment.entity.FailureReason;
import org.jspecify.annotations.Nullable;

/**
 * PG 승인 결과다.
 *
 * @param pgTransactionId 승인 거래 식별자. 승인일 때만 있다
 * @param failureReason 거절 사유. 거절일 때만 있다
 */
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

    /** 승인 결과를 만든다. */
    public static PaymentApproval approved(String pgTransactionId) {
        return new PaymentApproval(true, pgTransactionId, null);
    }

    /** 거절 결과를 만든다. */
    public static PaymentApproval declined(FailureReason failureReason) {
        return new PaymentApproval(false, null, failureReason);
    }
}
