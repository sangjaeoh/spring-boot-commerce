package com.commerce.external.payment;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 연습용 동기 PG stub이다. 모든 승인을 즉시 성공 처리해 거래 ID를 발급하고, 취소는 취소 거래 ID를 발급한다.
 *
 * <p>실 PG·실패 시뮬레이션·거래 상태 보관은 범위 밖이다(기준선: 동기 stub). 실패 분기·보상 경로는 체크아웃
 * 파사드가 테스트 더블로 검증한다.
 */
@Component
public final class StubPaymentGateway implements PaymentGateway {

    @Override
    public PaymentApproval approve(Money amount, PaymentMethod method) {
        return PaymentApproval.approved("STUB-APPROVE-" + UUID.randomUUID());
    }

    @Override
    public String cancel(String pgTransactionId, String idempotencyKey) {
        // 실 어댑터는 idempotencyKey를 벤더 Idempotency-Key로 전달해 재시도 이중 환불을 막는다. stub은 범위 밖.
        return "STUB-CANCEL-" + UUID.randomUUID();
    }
}
