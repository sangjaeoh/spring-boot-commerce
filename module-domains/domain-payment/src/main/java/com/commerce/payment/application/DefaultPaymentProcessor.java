package com.commerce.payment.application;

import com.commerce.payment.application.info.GatewayTransactionInfo;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentProcessor;
import com.commerce.payment.application.required.PaymentApproval;
import com.commerce.payment.application.required.PaymentGateway;
import com.commerce.payment.application.required.PaymentRepository;
import com.commerce.payment.domain.FailureReason;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.domain.PaymentStatus;
import com.commerce.payment.domain.exception.PaymentErrorCode;
import com.commerce.payment.domain.exception.PaymentNotFoundException;
import com.commerce.payment.domain.exception.PaymentStatusException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link PaymentProcessor}의 기본 구현이다. */
@Service
class DefaultPaymentProcessor implements PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    DefaultPaymentProcessor(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
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

    @Override
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
        // 환불은 비가역이라 승인의 고아 청구처럼 역보상할 수 없다.
        String pgCancelTransactionId = paymentGateway.cancel(pgTransactionId, refundIdempotencyKey(pgTransactionId));
        inTransaction(() -> recordCancellation(paymentId, pgCancelTransactionId));
    }

    @Override
    public GatewayTransactionInfo inquireGateway(UUID paymentId) {
        find(paymentId);
        return GatewayTransactionInfo.from(paymentGateway.inquire(paymentId));
    }

    @Override
    public PaymentInfo confirmApproval(UUID paymentId, String pgTransactionId) {
        return inTransaction(() -> recordApproval(paymentId, PaymentApproval.approved(pgTransactionId)));
    }

    @Override
    public PaymentInfo confirmFailure(UUID paymentId, FailureReason failureReason) {
        return inTransaction(() -> recordApproval(paymentId, PaymentApproval.declined(failureReason)));
    }

    @Override
    public PaymentInfo confirmOrphanedApproval(UUID paymentId, String pgTransactionId) {
        String pgCancelTransactionId = paymentGateway.cancel(pgTransactionId, refundIdempotencyKey(pgTransactionId));
        return inTransaction(() -> recordRefundedApproval(paymentId, pgTransactionId, pgCancelTransactionId));
    }

    /** PG를 생략한 자동 승인을 기록한다. */
    private PaymentInfo recordAutoApproval(UUID paymentId) {
        Payment payment = find(paymentId);
        payment.approveWithoutGateway(clock.instant());
        return PaymentInfo.from(payment);
    }

    /** PG 승인 결과를 승인 또는 실패로 기록한다. */
    private PaymentInfo recordApproval(UUID paymentId, PaymentApproval approval) {
        Payment payment = find(paymentId);
        if (approval.approved()) {
            payment.approve(Objects.requireNonNull(approval.pgTransactionId()), clock.instant());
        } else {
            payment.fail(Objects.requireNonNull(approval.failureReason()));
        }
        return PaymentInfo.from(payment);
    }

    /** 취소·환불 결과를 기록한다. */
    private PaymentInfo recordCancellation(UUID paymentId, @Nullable String pgCancelTransactionId) {
        Payment payment = find(paymentId);
        payment.cancel(pgCancelTransactionId, clock.instant());
        return PaymentInfo.from(payment);
    }

    /** 고아 청구의 승인과 취소를 한 커밋으로 기록한다. */
    private PaymentInfo recordRefundedApproval(UUID paymentId, String pgTransactionId, String pgCancelTransactionId) {
        Payment payment = find(paymentId);
        payment.approve(pgTransactionId, clock.instant());
        payment.cancel(pgCancelTransactionId, clock.instant());
        return PaymentInfo.from(payment);
    }

    /** 환불 멱등 키를 원거래 ID에서 결정론적으로 파생한다. */
    private static String refundIdempotencyKey(String pgTransactionId) {
        // 시간·UUID·카운터를 섞으면 재시도가 다른 키를 내 이중 환불 창이 다시 열리므로 순수 함수로 유지한다.
        return "CANCEL:" + pgTransactionId;
    }

    /** 결과 영속에 실패한 승인 청구를 환불한다. */
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

    /** 기록 동작을 트랜잭션 안에서 실행한다. */
    private PaymentInfo inTransaction(Supplier<PaymentInfo> action) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    /** 결제를 찾고 없으면 거부한다. */
    private Payment find(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
