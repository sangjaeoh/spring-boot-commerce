package com.commerce.domain.payment.application.required;

import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.shared.entity.Money;
import java.util.UUID;

/** 결제 승인·취소·거래 상태 조회를 위임하는 벤더 중립 포트다. */
public interface PaymentGateway {

    /** 결제를 승인한다. {@code paymentId}는 가맹점 참조로 전달돼 이후 {@link #inquire} 조회 키가 된다. */
    PaymentApproval approve(UUID paymentId, Money amount, PaymentMethod method);

    /**
     * 승인 거래를 취소·환불하고 취소 거래 ID를 반환한다.
     *
     * <p>{@code idempotencyKey}로 멱등해야 한다: 같은 키의 반복 취소는 환불을 정확히 한 번만 수행하고 최초 취소
     * 결과를 그대로 반환한다.
     */
    String cancel(String pgTransactionId, String idempotencyKey);

    /**
     * 승인 거래를 부분 금액으로 취소·환불하고 취소 거래 ID를 반환한다.
     *
     * <p>{@code idempotencyKey}로 멱등해야 한다: 같은 키의 반복 취소는 환불을 정확히 한 번만 수행하고 최초 취소
     * 결과를 그대로 반환한다.
     */
    String cancelPartially(String pgTransactionId, Money amount, String idempotencyKey);

    /** 결제의 PG 거래 상태를 가맹점 참조(결제 ID)로 조회한다. */
    GatewayTransactionStatus inquire(UUID paymentId);
}
