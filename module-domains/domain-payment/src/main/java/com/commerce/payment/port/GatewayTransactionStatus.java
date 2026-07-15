package com.commerce.payment.port;

import com.commerce.payment.entity.FailureReason;
import org.jspecify.annotations.Nullable;

/**
 * PG 거래 상태 조회 결과다. 승인이면 거래 ID를, 거절이면 사유를 담는다. {@code NOT_FOUND}는 해당 결제의 청구가
 * PG에 도달하지 않았음을 뜻한다 — 돈이 움직이지 않았으므로 호출자는 실패로 확정해도 안전하다.
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

    public static GatewayTransactionStatus approved(String pgTransactionId) {
        return new GatewayTransactionStatus(Result.APPROVED, pgTransactionId, null);
    }

    public static GatewayTransactionStatus declined(FailureReason failureReason) {
        return new GatewayTransactionStatus(Result.DECLINED, null, failureReason);
    }

    public static GatewayTransactionStatus notFound() {
        return new GatewayTransactionStatus(Result.NOT_FOUND, null, null);
    }

    /** PG 거래 조회 판정이다. */
    public enum Result {
        APPROVED,
        DECLINED,
        NOT_FOUND
    }
}
