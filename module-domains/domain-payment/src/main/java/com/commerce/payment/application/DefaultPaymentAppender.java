package com.commerce.payment.application;

import com.commerce.payment.application.provided.PaymentAppender;
import com.commerce.payment.application.required.PaymentRepository;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.payment.domain.exception.DuplicatePaymentException;
import com.commerce.payment.domain.exception.PaymentErrorCode;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link PaymentAppender}의 기본 구현이다. */
@Service
class DefaultPaymentAppender implements PaymentAppender {

    private final PaymentRepository paymentRepository;

    DefaultPaymentAppender(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    @Override
    public UUID request(UUID orderId, Money amount, @Nullable PaymentMethod method) {
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new DuplicatePaymentException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }
        try {
            return paymentRepository
                    .saveAndFlush(Payment.request(orderId, amount, method))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 요청 경합 방어
            throw new DuplicatePaymentException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }
    }
}
