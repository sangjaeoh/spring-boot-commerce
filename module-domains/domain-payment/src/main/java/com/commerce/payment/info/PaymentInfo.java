package com.commerce.payment.info;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 결제 조회 경계 모델이다. 체크아웃 파사드가 승인/실패를 이 결과로 분기한다. */
public record PaymentInfo(
        UUID id,
        UUID orderId,
        Money amount,
        PaymentStatus status,
        @Nullable PaymentMethod method,
        @Nullable FailureReason failureReason,
        @Nullable String pgTransactionId,
        @Nullable String pgCancelTransactionId,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getFailureReason(),
                payment.getPgTransactionId(),
                payment.getPgCancelTransactionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
