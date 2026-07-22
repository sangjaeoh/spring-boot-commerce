package com.commerce.order.info;

import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.entity.ReturnStatus;
import com.commerce.shared.entity.Money;
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
        @Nullable Instant stockDeductedAt,
        @Nullable Instant paidAt,
        @Nullable Instant shippedAt,
        @Nullable String carrier,
        @Nullable String trackingNumber,
        @Nullable Instant deliveredAt,
        @Nullable Instant cancelledAt,
        @Nullable CancellationReason cancellationReason,
        @Nullable HoldReason holdReason,
        @Nullable Instant refundedAt,
        @Nullable RefundReason refundReason,
        @Nullable ReturnStatus returnStatus,
        @Nullable Instant returnRequestedAt,
        @Nullable RefundReason returnReason,
        Instant createdAt,
        Instant updatedAt) {

    public OrderInfo {
        lines = List.copyOf(lines);
    }

    /** 주문 엔티티에서 조회 모델을 만든다. */
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
                order.getStockDeductedAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                order.getHoldReason(),
                order.getRefundedAt(),
                order.getRefundReason(),
                order.getReturnStatus(),
                order.getReturnRequestedAt(),
                order.getReturnReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
