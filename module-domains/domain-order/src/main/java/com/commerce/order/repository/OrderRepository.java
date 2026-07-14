package com.commerce.order.repository;

import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByMemberIdAndStatusAndFulfillmentStatusNot(
            UUID memberId, OrderStatus status, FulfillmentStatus fulfillmentStatus);
}
