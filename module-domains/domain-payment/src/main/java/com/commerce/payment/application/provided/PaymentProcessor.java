package com.commerce.payment.application.provided;

import com.commerce.payment.application.info.GatewayTransactionInfo;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.domain.FailureReason;
import com.commerce.payment.domain.exception.PaymentNotFoundException;
import com.commerce.payment.domain.exception.PaymentStatusException;
import java.util.UUID;

/** 결제 승인·취소와 리컨실 확정 기록을 조율하는 서비스다. PG 호출은 포트에 위임한다. */
public interface PaymentProcessor {

    /**
     * 결제를 승인하고 결과를 반환한다. 금액이 0이면 PG를 생략하고 자동 승인한다. 청구 후 결과 영속이 실패하면 그
     * 청구를 환불한다(고아 청구 방지).
     *
     * @throws PaymentStatusException 요청 상태가 아니면(재요청은 PG를 청구하지 않고 거부된다)
     */
    PaymentInfo approve(UUID paymentId);

    /**
     * 결제를 취소·환불한다. PG 미호출 승인(전액 할인)이면 환불 호출을 생략하고, 이미 취소된 결제는 PG 재호출
     * 없이 조용히 통과시켜 환불이 최대 한 번만 일어나게 한다. 환불 성공 후 영속이 실패해도 재시도가 같은 멱등
     * 키로 환불을 재호출한다. 호출자는 이 메서드를 트랜잭션 밖에서 부른다.
     *
     * @throws PaymentStatusException 승인·취소 상태가 아니면
     */
    void cancel(UUID paymentId);

    /**
     * 결제의 PG 거래 상태를 조회한다. 결제 상태는 바꾸지 않으며, PG 호출이므로 호출자는 트랜잭션 밖에서 부른다.
     *
     * @throws PaymentNotFoundException 결제가 없으면
     */
    GatewayTransactionInfo inquireGateway(UUID paymentId);

    /**
     * PG 상태 조회로 확정된 승인을 기록한다. PG를 재청구하지 않고 결과만 반영한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    PaymentInfo confirmApproval(UUID paymentId, String pgTransactionId);

    /**
     * PG 상태 조회로 확정된 실패(거절·청구 미도달)를 기록한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    PaymentInfo confirmFailure(UUID paymentId, FailureReason failureReason);

    /**
     * 주문이 이미 취소·환불로 종결된 결제의 지연 승인(고아 청구)을 환불로 종결한다. PG 환불을 먼저 수행하고
     * 승인·취소 기록을 한 커밋으로 남긴다. 호출자는 이 메서드를 트랜잭션 밖에서 부른다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    PaymentInfo confirmOrphanedApproval(UUID paymentId, String pgTransactionId);
}
