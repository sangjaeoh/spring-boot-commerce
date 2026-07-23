package com.commerce.domain.payment.application.info;

import com.commerce.domain.payment.domain.FailureReason;
import com.commerce.domain.payment.domain.Payment;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.payment.domain.PaymentStatus;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 결제 조회 경계 모델이다. */
public record PaymentInfo(
        UUID id,
        UUID orderId,
        Money amount,
        Money refundedAmount,
        PaymentStatus status,
        @Nullable PaymentMethod method,
        @Nullable FailureReason failureReason,
        @Nullable String pgTransactionId,
        @Nullable String pgCancelTransactionId,
        @Nullable Instant approvedAt,
        @Nullable Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt) {

    /** 결제 엔티티에서 조회 모델을 만든다. */
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getFailureReason(),
                payment.getPgTransactionId(),
                payment.getPgCancelTransactionId(),
                payment.getApprovedAt(),
                payment.getCancelledAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
