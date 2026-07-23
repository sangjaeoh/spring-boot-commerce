package com.commerce.domain.payment.application;

import com.commerce.domain.payment.application.required.GatewayTransactionStatus;
import com.commerce.domain.payment.application.required.PaymentApproval;
import com.commerce.domain.payment.application.required.PaymentGateway;
import com.commerce.domain.payment.domain.FailureReason;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.shared.entity.Money;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 승인/거절을 전환할 수 있는 테스트용 PG 스텁이다. 승인·거절 거래를 결제 ID로 보관해 상태 조회에 재생한다.
 * 취소는 {@code idempotencyKey}로 멱등하다 — 같은 키의 반복 취소는 최초 결과를 재생하고 환불을 다시 세지
 * 않는다. {@code refundCount}로 실제 환불 횟수를 관측한다.
 */
class StubPaymentGateway implements PaymentGateway {

    boolean decline = false;
    boolean cancelCalled = false;
    int refundCount = 0;
    private final Map<String, String> resultByKey = new HashMap<>();
    private final Map<UUID, GatewayTransactionStatus> transactionsByPaymentId = new HashMap<>();

    void reset() {
        decline = false;
        cancelCalled = false;
        refundCount = 0;
        resultByKey.clear();
        transactionsByPaymentId.clear();
    }

    @Override
    public PaymentApproval approve(UUID paymentId, Money amount, PaymentMethod method) {
        if (decline) {
            transactionsByPaymentId.put(
                    paymentId, GatewayTransactionStatus.declined(FailureReason.INSUFFICIENT_BALANCE));
            return PaymentApproval.declined(FailureReason.INSUFFICIENT_BALANCE);
        }
        String pgTransactionId = "PG-" + amount.amount();
        transactionsByPaymentId.put(paymentId, GatewayTransactionStatus.approved(pgTransactionId));
        return PaymentApproval.approved(pgTransactionId);
    }

    @Override
    public GatewayTransactionStatus inquire(UUID paymentId) {
        return transactionsByPaymentId.getOrDefault(paymentId, GatewayTransactionStatus.notFound());
    }

    @Override
    public String cancel(String pgTransactionId, String idempotencyKey) {
        cancelCalled = true;
        @Nullable String cached = resultByKey.get(idempotencyKey);
        if (cached != null) {
            return cached;
        }
        refundCount++;
        String result = "CANCEL-" + pgTransactionId;
        resultByKey.put(idempotencyKey, result);
        return result;
    }

    @Override
    public String cancelPartially(String pgTransactionId, Money amount, String idempotencyKey) {
        cancelCalled = true;
        @Nullable String cached = resultByKey.get(idempotencyKey);
        if (cached != null) {
            return cached;
        }
        refundCount++;
        String result = "PARTIAL-CANCEL-" + pgTransactionId;
        resultByKey.put(idempotencyKey, result);
        return result;
    }
}
