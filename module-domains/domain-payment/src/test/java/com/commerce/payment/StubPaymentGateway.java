package com.commerce.payment;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;

/** 승인/거절을 전환할 수 있고 취소 호출 여부를 기록하는 테스트용 PG 스텁이다. */
class StubPaymentGateway implements PaymentGateway {

    boolean decline = false;
    boolean cancelCalled = false;

    void reset() {
        decline = false;
        cancelCalled = false;
    }

    @Override
    public PaymentApproval approve(Money amount, PaymentMethod method) {
        return decline
                ? PaymentApproval.declined(FailureReason.INSUFFICIENT_BALANCE)
                : PaymentApproval.approved("PG-" + amount.amount());
    }

    @Override
    public String cancel(String pgTransactionId) {
        cancelCalled = true;
        return "CANCEL-" + pgTransactionId;
    }
}
