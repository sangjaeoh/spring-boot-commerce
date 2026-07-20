package com.commerce.order.service;

import com.commerce.order.entity.Address;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.exception.InvalidOrderException;
import com.commerce.order.repository.OrderRepository;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성을 담당하는 서비스다. */
@Service
public class OrderAppender {

    private final OrderRepository orderRepository;

    public OrderAppender(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 주문을 생성하고 새 주문 ID를 반환한다.
     *
     * @throws InvalidOrderException 라인이 없거나 할인 불변식을 어기면
     */
    @Transactional
    public UUID place(
            UUID memberId,
            List<OrderLineSnapshot> lineSnapshots,
            Address shippingAddress,
            Money discountAmount,
            Money shippingFee,
            @Nullable UUID issuedCouponId) {
        Order order =
                Order.place(memberId, lineSnapshots, shippingAddress, discountAmount, shippingFee, issuedCouponId);
        return orderRepository.save(order).getId();
    }
}
