package com.commerce.order.application;

import com.commerce.event.order.OrderPaid;
import com.commerce.event.publish.MessagePublisher;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.application.required.OrderRepository;
import com.commerce.order.domain.CancellationReason;
import com.commerce.order.domain.HoldReason;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.RefundReason;
import com.commerce.order.domain.exception.OrderErrorCode;
import com.commerce.order.domain.exception.OrderNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderModifier}의 기본 구현이다. */
@Service
class DefaultOrderModifier implements OrderModifier {

    private final OrderRepository orderRepository;
    private final MessagePublisher messagePublisher;
    private final Clock clock;

    DefaultOrderModifier(OrderRepository orderRepository, MessagePublisher messagePublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.messagePublisher = messagePublisher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void markPaid(UUID orderId) {
        Order order = find(orderId);
        order.markPaid(clock.instant());
        messagePublisher.publish(new OrderPaid(order.getId(), order.getMemberId(), order.getOrderedVariantIds()));
    }

    @Transactional
    @Override
    public void markStockDeducted(UUID orderId) {
        find(orderId).markStockDeducted(clock.instant());
    }

    @Transactional
    @Override
    public void cancel(UUID orderId, CancellationReason reason) {
        find(orderId).cancel(reason, clock.instant());
    }

    @Transactional
    @Override
    public void requestCancellation(UUID orderId) {
        find(orderId).requestCancellation(clock.instant());
    }

    @Transactional
    @Override
    public void refund(UUID orderId, RefundReason reason) {
        find(orderId).refund(reason, clock.instant());
    }

    @Transactional
    @Override
    public void requestReturn(UUID orderId, UUID memberId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestReturn(reason, clock.instant());
    }

    @Transactional
    @Override
    public void rejectReturn(UUID orderId) {
        find(orderId).rejectReturn();
    }

    @Transactional
    @Override
    public void ship(UUID orderId, String carrier, String trackingNumber) {
        find(orderId).ship(carrier, trackingNumber, clock.instant());
    }

    @Transactional
    @Override
    public void confirmDelivery(UUID orderId) {
        find(orderId).confirmDelivery(clock.instant());
    }

    @Transactional
    @Override
    public void holdFulfillment(UUID orderId, HoldReason reason) {
        find(orderId).holdFulfillment(reason);
    }

    @Transactional
    @Override
    public void releaseFulfillment(UUID orderId) {
        find(orderId).releaseFulfillment();
    }

    /** 주문을 찾고 없으면 거부한다. */
    private Order find(UUID orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
