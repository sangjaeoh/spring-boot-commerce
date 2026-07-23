package com.commerce.domain.payment.adapter.integration;

import com.commerce.domain.payment.application.required.GatewayTransactionStatus;
import com.commerce.domain.payment.application.required.PaymentApproval;
import com.commerce.domain.payment.application.required.PaymentGateway;
import com.commerce.domain.payment.domain.FailureReason;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.shared.entity.Money;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 실패·응답 유실을 트리거 금액으로 시뮬레이션하는 연습용 fake PG다. */
@Component
public final class FakePaymentGateway implements PaymentGateway {

    private static final long DECLINE_TRIGGER = 999L;
    private static final long LOST_RESPONSE_TRIGGER = 998L;

    private final ConcurrentMap<UUID, GatewayTransactionStatus> transactionsByPaymentId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> cancelTransactionIdsByIdempotencyKey = new ConcurrentHashMap<>();

    /**
     * 트리거 금액으로 결과를 가른다 — 끝 세 자리 {@code 999}는 잔액 부족 거절, {@code 998}은 승인 거래를 기록한 뒤
     * 응답 유실, 그 외는 즉시 승인이다.
     *
     * @throws FakeGatewayTimeoutException 끝 세 자리가 {@code 998}이면 승인 거래가 PG에 기록된 채 전파된다
     */
    @Override
    public PaymentApproval approve(UUID paymentId, Money amount, PaymentMethod method) {
        long trigger = amount.amount() % 1000;
        if (trigger == DECLINE_TRIGGER) {
            transactionsByPaymentId.put(
                    paymentId, GatewayTransactionStatus.declined(FailureReason.INSUFFICIENT_BALANCE));
            return PaymentApproval.declined(FailureReason.INSUFFICIENT_BALANCE);
        }
        String pgTransactionId = "FAKE-APPROVE-" + UUID.randomUUID();
        transactionsByPaymentId.put(paymentId, GatewayTransactionStatus.approved(pgTransactionId));
        if (trigger == LOST_RESPONSE_TRIGGER) {
            throw new FakeGatewayTimeoutException(paymentId);
        }
        return PaymentApproval.approved(pgTransactionId);
    }

    @Override
    public String cancel(String pgTransactionId, String idempotencyKey) {
        return cancelTransactionIdsByIdempotencyKey.computeIfAbsent(
                idempotencyKey, key -> "FAKE-CANCEL-" + UUID.randomUUID());
    }

    @Override
    public String cancelPartially(String pgTransactionId, Money amount, String idempotencyKey) {
        return cancelTransactionIdsByIdempotencyKey.computeIfAbsent(
                idempotencyKey, key -> "FAKE-PARTIAL-CANCEL-" + UUID.randomUUID());
    }

    @Override
    public GatewayTransactionStatus inquire(UUID paymentId) {
        return transactionsByPaymentId.getOrDefault(paymentId, GatewayTransactionStatus.notFound());
    }
}
