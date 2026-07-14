package com.commerce.order.service;

import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.Order;
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

    public OrderModifier(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 결제를 완료한다. */
    @Transactional
    public void markPaid(UUID orderId) {
        find(orderId).markPaid();
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

    /** 출고한다. */
    @Transactional
    public void ship(UUID orderId) {
        find(orderId).ship();
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
