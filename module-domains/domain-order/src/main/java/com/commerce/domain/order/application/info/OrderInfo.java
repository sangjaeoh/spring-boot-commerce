package com.commerce.domain.order.application.info;

import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.HoldReason;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.ReturnStatus;
import com.commerce.domain.shared.entity.Money;
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
        Money refundedAmount,
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
                order.getRefundedAmount(),
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
