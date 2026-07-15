package com.commerce.external.payment;

import com.commerce.core.money.Money;
import com.commerce.external.payment.exception.FakeGatewayTimeoutException;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.GatewayTransactionStatus;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 실패·응답 유실을 결정론적으로 시뮬레이션하는 연습용 fake PG다. 거래를 가맹점 참조(결제 ID)로 보관해 상태
 * 조회와 멱등 환불을 지원한다.
 *
 * <p>샌드박스 트리거 금액(실 PG 샌드박스의 테스트 카드 번호와 같은 관례)으로 분기한다.
 *
 * <ul>
 *   <li>금액 끝 세 자리 999 — 잔액 부족으로 거절한다.
 *   <li>금액 끝 세 자리 998 — 승인 거래를 기록한 뒤 응답을 유실한다(타임아웃 예외). 청구는 PG에 존재하므로
 *       상태 조회만이 이 거래를 되찾는다.
 *   <li>그 외 — 즉시 승인한다.
 * </ul>
 *
 * <p>거래 보관이 인메모리라 프로세스 재시작 시 PG측 거래가 사라진다(실 PG는 지속). 재시작 전 승인분의 상태
 * 조회가 미도달로 판정되는 한계는 연습 범위로 수용한다.
 */
@Component
public final class FakePaymentGateway implements PaymentGateway {

    private static final long DECLINE_TRIGGER = 999L;
    private static final long LOST_RESPONSE_TRIGGER = 998L;

    private final ConcurrentMap<UUID, GatewayTransactionStatus> transactionsByPaymentId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> cancelTransactionIdsByIdempotencyKey = new ConcurrentHashMap<>();

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
    public GatewayTransactionStatus inquire(UUID paymentId) {
        return transactionsByPaymentId.getOrDefault(paymentId, GatewayTransactionStatus.notFound());
    }
}
