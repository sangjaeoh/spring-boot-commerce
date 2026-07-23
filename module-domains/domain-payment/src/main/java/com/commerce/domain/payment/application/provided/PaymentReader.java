package com.commerce.domain.payment.application.provided;

import com.commerce.domain.payment.application.info.PaymentInfo;
import com.commerce.domain.payment.domain.exception.PaymentNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 결제 조회를 담당하는 서비스다. */
public interface PaymentReader {

    /**
     * 주문의 결제를 조회한다. 주문당 결제는 최대 1행이다.
     *
     * @throws PaymentNotFoundException 주문의 결제가 없으면
     */
    PaymentInfo getByOrderId(UUID orderId);

    /**
     * 결제를 조회한다.
     *
     * @throws PaymentNotFoundException 결제가 없으면
     */
    PaymentInfo getPayment(UUID paymentId);

    /** 주문에 결제 행이 있는지 본다. */
    boolean hasPaymentForOrder(UUID orderId);

    /** 기준 시각 이전에 생성돼 아직 요청 상태인 결제를 조회한다. 리컨실 대상 선별용이며 없으면 빈 목록이다. */
    List<PaymentInfo> findRequestedBefore(Instant cutoff);
}
