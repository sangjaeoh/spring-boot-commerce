package com.commerce.payment.port;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.PaymentMethod;
import java.util.UUID;

/**
 * 결제 승인·취소·거래 상태 조회를 위임하는 벤더 중립 포트다. 구현은 외부 어댑터가 담당한다.
 */
public interface PaymentGateway {

    /**
     * 결제를 승인한다. {@code paymentId}는 가맹점 참조로 전달돼 이후 {@link #inquire} 조회 키가 된다 — 승인
     * 응답이 유실되면 호출자는 PG 거래 ID를 알 수 없어 가맹점 키로만 거래를 되찾는다.
     */
    PaymentApproval approve(UUID paymentId, Money amount, PaymentMethod method);

    /**
     * 승인 거래를 취소·환불하고 취소 거래 ID를 반환한다.
     *
     * <p>{@code idempotencyKey}로 멱등해야 한다: 같은 키의 반복 취소는 환불을 정확히 한 번만 수행하고 최초 취소
     * 결과를 그대로 반환한다. 환불은 비가역이라 결과 영속 실패 시 재시도가 같은 키로 이 메서드를 재호출하며, 이
     * 멱등성이 이중 환불을 막는 유일한 방어선이다. 구현 어댑터는 이 키를 벤더의 Idempotency-Key로 전달한다.
     */
    String cancel(String pgTransactionId, String idempotencyKey);

    /**
     * 결제의 PG 거래 상태를 가맹점 참조(결제 ID)로 조회한다. 승인 응답이 유실된 결제를 리컨실·웹훅 확정 경로가
     * 확정할 때 쓴다.
     */
    GatewayTransactionStatus inquire(UUID paymentId);
}
