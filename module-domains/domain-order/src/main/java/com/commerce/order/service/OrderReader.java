package com.commerce.order.service;

import com.commerce.order.entity.Order;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderNotFoundException;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.repository.OrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 조회를 담당한다. */
@Service
public class OrderReader {

    private final OrderRepository orderRepository;

    public OrderReader(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 주문을 조회한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(UUID orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.from(order);
    }
}
