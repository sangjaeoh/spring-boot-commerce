package com.commerce.payment.application;

import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.application.required.PaymentRepository;
import com.commerce.payment.domain.PaymentErrorCode;
import com.commerce.payment.domain.PaymentNotFoundException;
import com.commerce.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link PaymentReader}의 기본 구현이다. */
@Service
class DefaultPaymentReader implements PaymentReader {

    private final PaymentRepository paymentRepository;

    DefaultPaymentReader(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentInfo getByOrderId(UUID orderId) {
        return paymentRepository
                .findByOrderId(orderId)
                .map(PaymentInfo::from)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentInfo getPayment(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .map(PaymentInfo::from)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasPaymentForOrder(UUID orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PaymentInfo> findRequestedBefore(Instant cutoff) {
        return paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.REQUESTED, cutoff).stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
