package com.commerce.payment.service;

import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.exception.DuplicatePaymentException;
import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 요청 생성을 담당하는 서비스다. */
@Service
public class PaymentAppender {

    private final PaymentRepository paymentRepository;

    public PaymentAppender(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * 주문에 결제를 요청하고 새 결제 ID를 반환한다.
     *
     * @throws DuplicatePaymentException 이미 결제가 있는 주문이면
     */
    @Transactional
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
