package com.commerce.order.service;

import com.commerce.messaging.publish.MessagePublisher;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.event.OrderPaid;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderNotFoundException;
import com.commerce.order.exception.OrderStatusException;
import com.commerce.order.repository.OrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 결제·이행 상태 전이를 담당한다. */
@Service
public class OrderModifier {

    private final OrderRepository orderRepository;
    private final MessagePublisher messagePublisher;

    public OrderModifier(OrderRepository orderRepository, MessagePublisher messagePublisher) {
        this.orderRepository = orderRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 결제를 완료하고 {@link OrderPaid}를 발행한다.
     *
     * <p>발행은 이 트랜잭션 안에서 일어나므로 커밋 후 소비 리스너가 커밋 후에만 통지받는다.
     */
    @Transactional
    public void markPaid(UUID orderId) {
        Order order = find(orderId);
        order.markPaid();
        messagePublisher.publish(new OrderPaid(order.getId(), order.getMemberId(), order.getOrderedVariantIds()));
    }

    /**
     * 전 라인 재고 차감 완료 증거를 기록한다.
     *
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    @Transactional
    public void markStockDeducted(UUID orderId) {
        find(orderId).markStockDeducted();
    }

    /**
     * 주문을 취소한다.
     *
     * @throws OrderStatusException 이미 취소됐거나 출고 이후면
     */
    @Transactional
    public void cancel(UUID orderId, CancellationReason reason) {
        find(orderId).cancel(reason);
    }

    /**
     * 배송 완료 주문을 전체 반품 환불 처리한다.
     *
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    @Transactional
    public void refund(UUID orderId, RefundReason reason) {
        find(orderId).refund(reason);
    }

    /** 출고한다. 택배사·운송장 번호를 기록한다. */
    @Transactional
    public void ship(UUID orderId, String carrier, String trackingNumber) {
        find(orderId).ship(carrier, trackingNumber);
    }

    /** 배송 완료 처리한다. */
    @Transactional
    public void confirmDelivery(UUID orderId) {
        find(orderId).confirmDelivery();
    }

    /** 이행을 보류한다. */
    @Transactional
    public void holdFulfillment(UUID orderId, HoldReason reason) {
        find(orderId).holdFulfillment(reason);
    }

    /** 이행 보류를 해제한다. */
    @Transactional
    public void releaseFulfillment(UUID orderId) {
        find(orderId).releaseFulfillment();
    }

    private Order find(UUID orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
