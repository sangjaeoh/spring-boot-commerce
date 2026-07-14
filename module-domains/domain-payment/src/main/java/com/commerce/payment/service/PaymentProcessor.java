package com.commerce.payment.service;

import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.exception.PaymentNotFoundException;
import com.commerce.payment.exception.PaymentStatusException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.payment.repository.PaymentRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 승인·취소를 조율한다. PG 호출은 포트에 위임한다.
 *
 * <p>승인이 PG 청구 후 결과 영속에 실패하면 그 청구를 환불한다 — 청구가 환불 없이 고아로 남지 않게 한다.
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
        PaymentApproval approval = paymentGateway.approve(payment.getAmount(), payment.requireMethod());
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
     * @throws PaymentStatusException 승인 상태가 아니면
     */
    @Transactional
    public void cancel(UUID paymentId) {
        Payment payment = find(paymentId);
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        String pgTransactionId = payment.getPgTransactionId();
        @Nullable
        String pgCancelTransactionId = pgTransactionId == null ? null : paymentGateway.cancel(pgTransactionId);
        payment.cancel(pgCancelTransactionId);
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

    private void refundOrphanedCharge(PaymentApproval approval, RuntimeException persistFailure) {
        @Nullable String pgTransactionId = approval.pgTransactionId();
        if (!approval.approved() || pgTransactionId == null) {
            return;
        }
        try {
            paymentGateway.cancel(pgTransactionId);
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
