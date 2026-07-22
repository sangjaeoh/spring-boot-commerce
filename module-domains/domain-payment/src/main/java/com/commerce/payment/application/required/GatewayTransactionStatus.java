package com.commerce.payment.application.required;

import com.commerce.payment.domain.FailureReason;
import org.jspecify.annotations.Nullable;

/**
 * PG 거래 상태 조회 결과다.
 *
 * @param pgTransactionId 승인 거래 ID. 승인 판정일 때만 있다
 * @param failureReason 거절 사유. 거절 판정일 때만 있다
 */
public record GatewayTransactionStatus(
        Result result,
        @Nullable String pgTransactionId,
        @Nullable FailureReason failureReason) {

    public GatewayTransactionStatus {
        boolean hasTransactionId = pgTransactionId != null;
        boolean hasFailureReason = failureReason != null;
        if (hasTransactionId != (result == Result.APPROVED) || hasFailureReason != (result == Result.DECLINED)) {
            throw new IllegalArgumentException("승인이면 거래 ID만, 거절이면 사유만 있어야 한다");
        }
    }

    /** 승인 판정 결과를 만든다. */
    public static GatewayTransactionStatus approved(String pgTransactionId) {
        return new GatewayTransactionStatus(Result.APPROVED, pgTransactionId, null);
    }

    /** 거절 판정 결과를 만든다. */
    public static GatewayTransactionStatus declined(FailureReason failureReason) {
        return new GatewayTransactionStatus(Result.DECLINED, null, failureReason);
    }

    /** 거래 미도달 판정 결과를 만든다. */
    public static GatewayTransactionStatus notFound() {
        return new GatewayTransactionStatus(Result.NOT_FOUND, null, null);
    }

    /** PG 거래 조회 판정이다. */
    public enum Result {
        /** 승인된 거래. */
        APPROVED,
        /** 거절된 거래. */
        DECLINED,
        /** PG에 도달하지 않은 청구. */
        NOT_FOUND
    }
}
