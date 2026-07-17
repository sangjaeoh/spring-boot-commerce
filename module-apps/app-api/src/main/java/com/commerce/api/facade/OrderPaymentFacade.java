package com.commerce.api.facade;

import com.commerce.order.exception.OrderNotFoundException;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.exception.PaymentNotFoundException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.service.PaymentReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 본인 주문의 결제 정보 조회를 조율한다.
 *
 * <p>주문(소유권)과 결제(거래) 두 도메인을 엮는 조회다. 주문 Reader로 본인 주문 소유권을 먼저 게이트하고 —
 * 타인 주문은 존재 누출 방지로 미존재로 취급 — 통과하면 결제 Reader로 승인·환불 거래를 조회한다. 트랜잭션을
 * 열지 않고 각 도메인 Reader가 자기 트랜잭션에서 Info까지 변환한다.
 */
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
        orderReader.getOrder(orderId, memberId); // 소유권 게이트 — 본인 주문이 아니면 미존재로 끝난다.
        return paymentReader.getByOrderId(orderId);
    }
}
