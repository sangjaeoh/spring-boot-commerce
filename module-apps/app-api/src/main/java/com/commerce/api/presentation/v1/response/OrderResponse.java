package com.commerce.api.presentation.v1.response;

import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.info.OrderInfo;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 상세 응답이다. 결제·이행 축 상태와 이력 시각·사유를 싣는다. */
public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID memberId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        long totalAmount,
        long discountAmount,
        long shippingFee,
        long payAmount,
        @Nullable UUID issuedCouponId,
        AddressResponse shippingAddress,
        List<OrderLineResponse> lines,
        Instant createdAt,
        @Nullable Instant paidAt,
        @Nullable Instant shippedAt,
        @Nullable Instant deliveredAt,
        @Nullable Instant cancelledAt,
        @Nullable CancellationReason cancellationReason,
        @Nullable HoldReason holdReason,
        @Nullable Instant refundedAt,
        @Nullable RefundReason refundReason) {

    public OrderResponse {
        lines = List.copyOf(lines);
    }

    public static OrderResponse from(OrderInfo order) {
        return new OrderResponse(
                order.id(),
                order.orderNumber(),
                order.memberId(),
                order.status(),
                order.fulfillmentStatus(),
                order.totalAmount().amount(),
                order.discountAmount().amount(),
                order.shippingFee().amount(),
                order.payAmount().amount(),
                order.issuedCouponId(),
                AddressResponse.from(order.shippingAddress()),
                order.lines().stream()
                        .map(OrderLineResponse::from)
                        .sorted(Comparator.comparing(OrderLineResponse::variantId))
                        .toList(),
                order.createdAt(),
                order.paidAt(),
                order.shippedAt(),
                order.deliveredAt(),
                order.cancelledAt(),
                order.cancellationReason(),
                order.holdReason(),
                order.refundedAt(),
                order.refundReason());
    }
}
