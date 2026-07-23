package com.commerce.api.facade;

import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.exception.OrderNotFoundException;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.domain.exception.PaymentNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 본인 주문의 결제 정보 조회를 조율하는 파사드다. */
@Component
public class OrderPaymentFacade {

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;

    public OrderPaymentFacade(OrderReader orderReader, PaymentReader paymentReader) {
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
    }

    /**
     * 본인 주문의 결제 정보를 조회한다.
     *
     * @throws OrderNotFoundException 본인 주문이 아니면(존재 누출 방지로 미존재 취급)
     * @throws PaymentNotFoundException 주문의 결제가 없으면
     */
    public PaymentInfo getPayment(UUID orderId, UUID memberId) {
        // 1. 소유권 게이트
        // 반환값을 버리고 본인 주문 여부 확인에만 쓴다.
        orderReader.getOrder(orderId, memberId);
        // 2. 주문의 결제 조회
        return paymentReader.getByOrderId(orderId);
    }
}
