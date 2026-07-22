package com.commerce.order.service;

import com.commerce.event.publish.MessagePublisher;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.event.OrderPaid;
import com.commerce.order.exception.FulfillmentStatusException;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderNotFoundException;
import com.commerce.order.exception.OrderStatusException;
import com.commerce.order.repository.OrderRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 결제·이행 상태 전이를 담당하는 서비스다. */
@Service
public class OrderModifier {

    private final OrderRepository orderRepository;
    private final MessagePublisher messagePublisher;
    private final Clock clock;

    public OrderModifier(OrderRepository orderRepository, MessagePublisher messagePublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.messagePublisher = messagePublisher;
        this.clock = clock;
    }

    /**
     * 결제를 완료하고 {@link OrderPaid}를 발행한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    @Transactional
    public void markPaid(UUID orderId) {
        Order order = find(orderId);
        order.markPaid(clock.instant());
        messagePublisher.publish(new OrderPaid(order.getId(), order.getMemberId(), order.getOrderedVariantIds()));
    }

    /**
     * 전 라인 재고 차감 완료 증거를 기록한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    @Transactional
    public void markStockDeducted(UUID orderId) {
        find(orderId).markStockDeducted(clock.instant());
    }

    /**
     * 주문을 취소한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 이미 취소됐거나 출고 이후면
     */
    @Transactional
    public void cancel(UUID orderId, CancellationReason reason) {
        find(orderId).cancel(reason, clock.instant());
    }

    /**
     * 결제 완료 주문의 취소 개시를 기록한다. 마커가 있는 동안 출고가 거부된다. 이미 개시된 주문에는
     * 아무것도 하지 않는다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 완료 주문이 아니거나 출고 이후면
     */
    @Transactional
    public void requestCancellation(UUID orderId) {
        find(orderId).requestCancellation(clock.instant());
    }

    /**
     * 배송 완료 주문을 전체 반품 환불 처리한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    @Transactional
    public void refund(UUID orderId, RefundReason reason) {
        find(orderId).refund(reason, clock.instant());
    }

    /**
     * 회원 본인 주문의 반품을 요청한다. 타인 주문은 미존재로 취급한다.
     *
     * @throws OrderNotFoundException 본인 주문이 없으면
     * @throws OrderStatusException 이미 요청 중이거나, 배송 완료된 결제 주문이 아니면
     */
    @Transactional
    public void requestReturn(UUID orderId, UUID memberId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestReturn(reason, clock.instant());
    }

    /**
     * 반품 요청을 거절한다. 주문은 PAID·DELIVERED로 남는다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 반품 요청 상태가 아니면
     */
    @Transactional
    public void rejectReturn(UUID orderId) {
        find(orderId).rejectReturn();
    }

    /**
     * 출고한다. 택배사·운송장 번호를 기록한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나, 준비 중이 아니거나, 취소가 진행 중이면
     */
    @Transactional
    public void ship(UUID orderId, String carrier, String trackingNumber) {
        find(orderId).ship(carrier, trackingNumber, clock.instant());
    }

    /**
     * 배송 완료 처리한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 출고된 상태가 아니면
     */
    @Transactional
    public void confirmDelivery(UUID orderId) {
        find(orderId).confirmDelivery(clock.instant());
    }

    /**
     * 이행을 보류한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 준비 중이 아니면
     */
    @Transactional
    public void holdFulfillment(UUID orderId, HoldReason reason) {
        find(orderId).holdFulfillment(reason);
    }

    /**
     * 이행 보류를 해제한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 보류 중이 아니면
     */
    @Transactional
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
