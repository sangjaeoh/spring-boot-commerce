package com.commerce.domain.order.application.info;

import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.Fulfillment;
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

    /** 주문·이행 엔티티에서 조회 모델을 만든다. 이행 행이 없으면(미개시) 시작 전으로 합성한다. */
    public static OrderInfo of(Order order, @Nullable Fulfillment fulfillment) {
        return new OrderInfo(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getStatus(),
                fulfillment == null ? FulfillmentStatus.NOT_STARTED : fulfillment.getStatus(),
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
                fulfillment == null ? null : fulfillment.getShippedAt(),
                fulfillment == null ? null : fulfillment.getCarrier(),
                fulfillment == null ? null : fulfillment.getTrackingNumber(),
                fulfillment == null ? null : fulfillment.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                fulfillment == null ? null : fulfillment.getHoldReason(),
                order.getRefundedAt(),
                order.getRefundReason(),
                order.getReturnStatus(),
                order.getReturnRequestedAt(),
                order.getReturnReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
