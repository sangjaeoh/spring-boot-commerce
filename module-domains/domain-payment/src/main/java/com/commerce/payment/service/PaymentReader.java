package com.commerce.payment.service;

import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.exception.PaymentNotFoundException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 조회를 담당한다. */
@Service
public class PaymentReader {

    private final PaymentRepository paymentRepository;

    public PaymentReader(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * 주문의 결제를 조회한다. 주문당 결제는 최대 1행이다.
     *
     * @throws PaymentNotFoundException 주문의 결제가 없으면
     */
    @Transactional(readOnly = true)
    public PaymentInfo getByOrderId(UUID orderId) {
        return paymentRepository
                .findByOrderId(orderId)
                .map(PaymentInfo::from)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
