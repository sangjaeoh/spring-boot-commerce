package com.commerce.order.service;

import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
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

    /**
     * 회원에게 미배송 결제완료 주문(PAID이면서 아직 DELIVERED가 아닌 주문)이 있는지 본다.
     *
     * <p>회원 탈퇴 가드가 소비한다. PAID는 취소분을 제외하므로 미취소 조건을 함께 만족한다.
     */
    @Transactional(readOnly = true)
    public boolean hasUndeliveredPaidOrder(UUID memberId) {
        return orderRepository.existsByMemberIdAndStatusAndFulfillmentStatusNot(
                memberId, OrderStatus.PAID, FulfillmentStatus.DELIVERED);
    }
}
