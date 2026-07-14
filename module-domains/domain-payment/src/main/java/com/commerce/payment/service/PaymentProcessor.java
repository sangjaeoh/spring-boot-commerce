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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 승인·취소를 조율한다. PG 호출은 포트에 위임한다. */
@Service
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentProcessor(PaymentRepository paymentRepository, PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 결제를 승인한다. 금액이 0이면 PG를 생략하고 자동 승인한다. 결과는 동기로 반환한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    @Transactional
    public PaymentInfo approve(UUID paymentId) {
        Payment payment = find(paymentId);
        if (payment.requiresGatewayApproval()) {
            PaymentApproval result = paymentGateway.approve(payment.getAmount(), payment.requireMethod());
            if (result.approved()) {
                payment.approve(Objects.requireNonNull(result.pgTransactionId()));
            } else {
                payment.fail(Objects.requireNonNull(result.failureReason()));
            }
        } else {
            payment.approveWithoutGateway();
        }
        return PaymentInfo.from(payment);
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

    private Payment find(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
