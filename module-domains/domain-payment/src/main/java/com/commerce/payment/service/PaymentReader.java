package com.commerce.payment.service;

import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.exception.PaymentNotFoundException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
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

    /**
     * 결제를 조회한다.
     *
     * @throws PaymentNotFoundException 결제가 없으면
     */
    @Transactional(readOnly = true)
    public PaymentInfo getPayment(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .map(PaymentInfo::from)
                .orElseThrow(() -> new PaymentNotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 주문에 결제 행이 있는지 본다.
     *
     * <p>PENDING 주문 스윕이 관할을 가르는 데 소비한다 — 결제 행이 있으면 미확정 결제 리컨실 관할이라 스윕이 건드리지
     * 않고, 없으면 결제 요청 이전에 중단된 잔여라 스윕이 직접 보상한다.
     */
    @Transactional(readOnly = true)
    public boolean hasPaymentForOrder(UUID orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }

    /** 기준 시각 이전에 생성돼 아직 요청 상태인 결제를 조회한다. 리컨실 대상 선별용이며 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<PaymentInfo> findRequestedBefore(Instant cutoff) {
        return paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.REQUESTED, cutoff).stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
