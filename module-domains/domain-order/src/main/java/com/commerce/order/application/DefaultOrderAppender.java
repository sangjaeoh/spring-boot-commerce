package com.commerce.order.application;

import com.commerce.order.application.provided.OrderAppender;
import com.commerce.order.application.required.OrderRepository;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderAppender}의 기본 구현이다. */
@Service
class DefaultOrderAppender implements OrderAppender {

    private final OrderRepository orderRepository;

    DefaultOrderAppender(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    @Override
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
