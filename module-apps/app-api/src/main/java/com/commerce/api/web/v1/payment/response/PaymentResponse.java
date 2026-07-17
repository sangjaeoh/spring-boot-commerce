package com.commerce.api.web.v1.payment.response;

import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 주문 결제 응답이다. 승인·환불 거래 ID와 시각을 각각 싣는다.
 *
 * <p>전액 할인(0원) 결제는 PG를 생략하므로 수단·승인 거래 ID가 비어 있을 수 있다.
 */
public record PaymentResponse(
        PaymentStatus status,
        @Nullable PaymentMethod method,
        long amount,
        @Nullable FailureReason failureReason,
        @Nullable String pgTransactionId,
        @Nullable String pgCancelTransactionId,
        @Nullable Instant approvedAt,
        @Nullable Instant cancelledAt) {

    public static PaymentResponse from(PaymentInfo payment) {
        return new PaymentResponse(
                payment.status(),
                payment.method(),
                payment.amount().amount(),
                payment.failureReason(),
                payment.pgTransactionId(),
                payment.pgCancelTransactionId(),
                payment.approvedAt(),
                payment.cancelledAt());
    }
}
