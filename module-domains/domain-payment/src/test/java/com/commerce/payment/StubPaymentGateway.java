package com.commerce.payment;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 승인/거절을 전환할 수 있는 테스트용 PG 스텁이다. 취소는 {@code idempotencyKey}로 멱등하다 — 같은 키의 반복
 * 취소는 최초 결과를 재생하고 환불을 다시 세지 않는다. {@code refundCount}로 실제 환불 횟수를 관측한다.
 */
class StubPaymentGateway implements PaymentGateway {

    boolean decline = false;
    boolean cancelCalled = false;
    int refundCount = 0;
    private final Map<String, String> resultByKey = new HashMap<>();

    void reset() {
        decline = false;
        cancelCalled = false;
        refundCount = 0;
        resultByKey.clear();
    }

    @Override
    public PaymentApproval approve(Money amount, PaymentMethod method) {
        return decline
                ? PaymentApproval.declined(FailureReason.INSUFFICIENT_BALANCE)
                : PaymentApproval.approved("PG-" + amount.amount());
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
}
