package com.commerce.payment.service;

import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.exception.PaymentNotFoundException;
import com.commerce.payment.exception.PaymentStatusException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.GatewayTransactionStatus;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.payment.repository.PaymentRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 승인·취소·확정 기록을 조율한다. PG 호출은 포트에 위임한다.
 *
 * <p>승인·취소 모두 PG 호출을 트랜잭션 밖에서 내고 결과 영속만 좁힌다. 승인은 영속 실패 시 고아 청구를 환불로
 * 역보상한다(청구가 환불 없이 고아로 남지 않게). 취소는 환불이 비가역이라 역보상 대신 멱등 키로 재시도 이중
 * 환불을 막는다. 응답 유실로 요청 상태에 머문 결제는 확정 기록({@code confirm*})이 PG 재청구 없이 상태 조회
 * 결과만 반영하고, 주문이 이미 종결된 지연 승인은 환불을 선행한 뒤 승인·취소를 한 커밋으로 기록한다.
 */
@Service
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentProcessor(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 결제를 승인하고 결과를 반환한다. 금액이 0이면 PG를 생략하고 자동 승인한다. 청구 후 결과 영속이 실패하면 그
     * 청구를 환불한다(고아 청구 방지).
     *
     * @throws PaymentStatusException 요청 상태가 아니면(재요청은 PG를 청구하지 않고 거부된다)
     */
    public PaymentInfo approve(UUID paymentId) {
        Payment payment = find(paymentId);
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        if (!payment.requiresGatewayApproval()) {
            return inTransaction(() -> recordAutoApproval(paymentId));
        }
        PaymentApproval approval =
                paymentGateway.approve(payment.getId(), payment.getAmount(), payment.requireMethod());
        try {
            return inTransaction(() -> recordApproval(paymentId, approval));
        } catch (RuntimeException persistFailure) {
            refundOrphanedCharge(approval, persistFailure);
            throw persistFailure;
        }
    }

    /**
     * 결제를 취소·환불한다. PG 미호출 승인(전액 할인)이면 환불 호출을 생략한다.
     *
     * <p>승인 경로처럼 환불을 트랜잭션 밖에서 내고 결과 영속만 프로그램적 트랜잭션으로 좁힌다. 환불 성공 후 영속이
     * 실패하면 재시도가 같은 멱등 키로 환불을 재호출하고 PG가 이를 멱등 처리해 이중 환불을 막는다 — 환불은
     * 비가역이라 승인의 고아 청구와 달리 역보상할 수 없다. 호출자는 이 메서드를 트랜잭션 밖에서 부른다.
     *
     * <p>이미 취소된 결제는 관용하고 조용히 반환한다. 취소 후 다운스트림(주문 취소·복원)이 실패해 재시도되는
     * 경로에서 이미-환불·CANCELLED 결제를 '환불 완료'로 통과시켜 복원을 완결한다. 이 반환이 아래 PG 환불 호출
     * 앞이라 재시도가 PG를 재호출하지 않아 환불이 최대 한 번만 일어난다.
     *
     * @throws PaymentStatusException 승인·취소 상태가 아니면
     */
    public void cancel(UUID paymentId) {
        Payment payment = find(paymentId);
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        String pgTransactionId = payment.getPgTransactionId();
        if (pgTransactionId == null) {
            inTransaction(() -> recordCancellation(paymentId, null));
            return;
        }
        String pgCancelTransactionId = paymentGateway.cancel(pgTransactionId, refundIdempotencyKey(pgTransactionId));
        inTransaction(() -> recordCancellation(paymentId, pgCancelTransactionId));
    }

    /**
     * 결제의 PG 거래 상태를 조회한다. 응답 유실로 요청 상태에 머문 결제를 리컨실·웹훅 확정 경로가 확정할 때
     * 근거로 쓴다. PG 호출이므로 호출자는 트랜잭션 밖에서 부른다.
     *
     * @throws PaymentNotFoundException 결제가 없으면
     */
    public GatewayTransactionStatus inquireGateway(UUID paymentId) {
        find(paymentId);
        return paymentGateway.inquire(paymentId);
    }

    /**
     * PG 상태 조회로 확정된 승인을 기록한다. PG를 재청구하지 않고 결과만 반영한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public PaymentInfo confirmApproval(UUID paymentId, String pgTransactionId) {
        return inTransaction(() -> recordApproval(paymentId, PaymentApproval.approved(pgTransactionId)));
    }

    /**
     * PG 상태 조회로 확정된 실패(거절·청구 미도달)를 기록한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public PaymentInfo confirmFailure(UUID paymentId, FailureReason failureReason) {
        return inTransaction(() -> recordApproval(paymentId, PaymentApproval.declined(failureReason)));
    }

    /**
     * 주문이 이미 취소·환불로 종결된 결제의 지연 승인(고아 청구)을 환불로 종결한다. PG 환불을 트랜잭션 밖에서
     * 먼저 수행하고 승인·취소 기록을 한 커밋으로 남긴다 — 어느 지점에서 중단돼도 결제가 요청 상태로 남아 다음
     * 리컨실이 재시도하고, 환불 재호출은 결정론적 멱등 키가 이중 환불을 막는다. 호출자는 이 메서드를 트랜잭션
     * 밖에서 부른다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public PaymentInfo confirmOrphanedApproval(UUID paymentId, String pgTransactionId) {
        String pgCancelTransactionId = paymentGateway.cancel(pgTransactionId, refundIdempotencyKey(pgTransactionId));
        return inTransaction(() -> recordRefundedApproval(paymentId, pgTransactionId, pgCancelTransactionId));
    }

    private PaymentInfo recordAutoApproval(UUID paymentId) {
        Payment payment = find(paymentId);
        payment.approveWithoutGateway();
        return PaymentInfo.from(payment);
    }

    private PaymentInfo recordApproval(UUID paymentId, PaymentApproval approval) {
        Payment payment = find(paymentId);
        if (approval.approved()) {
            payment.approve(Objects.requireNonNull(approval.pgTransactionId()));
        } else {
            payment.fail(Objects.requireNonNull(approval.failureReason()));
        }
        return PaymentInfo.from(payment);
    }

    private PaymentInfo recordCancellation(UUID paymentId, @Nullable String pgCancelTransactionId) {
        Payment payment = find(paymentId);
        payment.cancel(pgCancelTransactionId);
        return PaymentInfo.from(payment);
    }

    private PaymentInfo recordRefundedApproval(UUID paymentId, String pgTransactionId, String pgCancelTransactionId) {
        Payment payment = find(paymentId);
        payment.approve(pgTransactionId);
        payment.cancel(pgCancelTransactionId);
        return PaymentInfo.from(payment);
    }

    /**
     * 환불 멱등 키를 원거래 ID에서 결정론적으로 파생한다. 영속 실패 후 재시도가 같은 키를 재생성해 PG가 이중
     * 환불을 막는다. 시간·UUID·카운터를 섞으면 창이 다시 열리므로 순수 함수로 유지한다.
     */
    private static String refundIdempotencyKey(String pgTransactionId) {
        return "CANCEL:" + pgTransactionId;
    }

    private void refundOrphanedCharge(PaymentApproval approval, RuntimeException persistFailure) {
        @Nullable String pgTransactionId = approval.pgTransactionId();
        if (!approval.approved() || pgTransactionId == null) {
            return;
        }
        try {
            paymentGateway.cancel(pgTransactionId, refundIdempotencyKey(pgTransactionId));
        } catch (RuntimeException refundFailure) {
            // 원인(영속 실패)을 보존한 채 환불 실패를 함께 노출한다 — 환불 실패는 수동 대사 대상이다.
            persistFailure.addSuppressed(refundFailure);
        }
    }

    private PaymentInfo inTransaction(Supplier<PaymentInfo> action) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    private Payment find(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
