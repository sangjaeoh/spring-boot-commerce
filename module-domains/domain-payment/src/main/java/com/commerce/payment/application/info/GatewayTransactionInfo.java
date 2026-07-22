package com.commerce.payment.application.info;

import com.commerce.payment.application.required.GatewayTransactionStatus;
import com.commerce.payment.domain.FailureReason;
import org.jspecify.annotations.Nullable;

/**
 * PG 거래 상태 조회 결과의 경계 모델이다.
 *
 * @param pgTransactionId 승인 거래 ID. 승인 판정일 때만 있다
 * @param failureReason 거절 사유. 거절 판정일 때만 있다
 */
public record GatewayTransactionInfo(
        Result result,
        @Nullable String pgTransactionId,
        @Nullable FailureReason failureReason) {

    /** required 조회 결과에서 경계 모델을 만든다. */
    public static GatewayTransactionInfo from(GatewayTransactionStatus status) {
        return new GatewayTransactionInfo(
                switch (status.result()) {
                    case APPROVED -> Result.APPROVED;
                    case DECLINED -> Result.DECLINED;
                    case NOT_FOUND -> Result.NOT_FOUND;
                },
                status.pgTransactionId(),
                status.failureReason());
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
