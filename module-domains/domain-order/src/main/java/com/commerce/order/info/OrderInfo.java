package com.commerce.order.info;

import com.commerce.core.money.Money;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 조회 경계 모델이다. */
public record OrderInfo(
        UUID id,
        String orderNumber,
        UUID memberId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        Money totalAmount,
        Money discountAmount,
        Money shippingFee,
        Money payAmount,
        @Nullable UUID issuedCouponId,
        AddressInfo shippingAddress,
        List<OrderLineInfo> lines,
        Instant createdAt,
        Instant updatedAt) {

    public OrderInfo {
        lines = List.copyOf(lines);
    }

    public static OrderInfo from(Order order) {
        return new OrderInfo(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getStatus(),
                order.getFulfillmentStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getPayAmount(),
                order.getIssuedCouponId(),
                AddressInfo.from(order.getShippingAddress()),
                order.getLines().stream().map(OrderLineInfo::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
