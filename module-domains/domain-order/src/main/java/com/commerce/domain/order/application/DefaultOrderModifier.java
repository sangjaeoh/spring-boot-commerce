package com.commerce.domain.order.application;

import com.commerce.common.event.publish.MessagePublisher;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.application.required.OrderRepository;
import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.HoldReason;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderLineStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderNotFoundException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.event.order.OrderPaid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderModifier}의 기본 구현이다. */
@Service
class DefaultOrderModifier implements OrderModifier {

    private final OrderRepository orderRepository;
    private final FulfillmentRepository fulfillmentRepository;
    private final MessagePublisher messagePublisher;
    private final Clock clock;

    DefaultOrderModifier(
            OrderRepository orderRepository,
            FulfillmentRepository fulfillmentRepository,
            MessagePublisher messagePublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
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
        find(orderId).cancel(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public Money beginLineCancellation(UUID orderId, UUID lineId) {
        return find(orderId).beginLineCancellation(lineId, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public boolean completeLineCancellation(UUID orderId, UUID lineId) {
        return find(orderId).completeLineCancellation(lineId, clock.instant());
    }

    @Transactional
    @Override
    public void requestCancellation(UUID orderId) {
        find(orderId).requestCancellation(fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void refund(UUID orderId, RefundReason reason) {
        find(orderId).refund(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void requestReturn(UUID orderId, UUID memberId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestReturn(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void requestLineReturn(UUID orderId, UUID memberId, UUID lineId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestLineReturn(lineId, reason, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public void rejectLineReturn(UUID orderId, UUID lineId) {
        find(orderId).rejectLineReturn(lineId);
    }

    @Transactional
    @Override
    public Money beginLineReturn(UUID orderId, UUID lineId) {
        return find(orderId).beginLineReturn(lineId, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public boolean completeLineReturn(UUID orderId, UUID lineId) {
        return find(orderId).completeLineReturn(lineId, clock.instant());
    }

    @Transactional
    @Override
    public void rejectReturn(UUID orderId) {
        find(orderId).rejectReturn();
    }

    @Transactional
    @Override
    public void ship(UUID orderId, String carrier, String trackingNumber) {
        Order order = find(orderId);
        boolean cancelInProgress = order.getCancelRequestedAt() != null
                || order.getLines().stream().anyMatch(line -> line.getStatus() == OrderLineStatus.CANCELLING);
        requireFulfillment(orderId, order).ship(carrier, trackingNumber, cancelInProgress, clock.instant());
    }

    @Transactional
    @Override
    public void confirmDelivery(UUID orderId) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).confirmDelivery(clock.instant());
    }

    @Transactional
    @Override
    public void holdFulfillment(UUID orderId, HoldReason reason) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).hold(reason);
    }

    @Transactional
    @Override
    public void releaseFulfillment(UUID orderId) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).release();
    }

    /** 주문을 찾고 없으면 거부한다. */
    private Order find(UUID orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /** 주문의 이행 축 상태를 읽는다. 이행 행이 없으면 시작 전이다(PENDING 주문의 정상 상태). */
    private FulfillmentStatus fulfillmentStatusOf(UUID orderId) {
        return fulfillmentRepository
                .findByOrderId(orderId)
                .map(Fulfillment::getStatus)
                .orElse(FulfillmentStatus.NOT_STARTED);
    }

    /** 결제 완료 주문의 이행 애그리거트를 찾는다. 결제 미완료거나 생성 반영 전이면 거부한다. */
    private Fulfillment requireFulfillment(UUID orderId, Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new FulfillmentStatusException(OrderErrorCode.NOT_PAID);
        }
        return fulfillmentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new FulfillmentStatusException(OrderErrorCode.FULFILLMENT_NOT_READY));
    }
}
