package com.commerce.payment.port;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.PaymentMethod;

/**
 * 결제 승인·취소를 위임하는 벤더 중립 포트다. 구현은 외부 어댑터가 담당한다.
 */
public interface PaymentGateway {

    /** 결제를 승인한다. */
    PaymentApproval approve(Money amount, PaymentMethod method);

    /** 승인 거래를 취소·환불하고 취소 거래 ID를 반환한다. */
    String cancel(String pgTransactionId);
}
