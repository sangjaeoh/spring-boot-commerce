package com.commerce.api.web.v1.order.response;

import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.info.OrderInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "주문 상세 응답. 결제·이행 축 상태와 이력 시각·사유·운송장 기록을 싣는다.")
public record OrderResponse(
        @Schema(description = "주문 ID") UUID id,
        @Schema(description = "주문 번호") String orderNumber,
        @Schema(description = "주문 회원 ID") UUID memberId,
        @Schema(description = "결제 축 상태") OrderStatus status,
        @Schema(description = "이행 축 상태") FulfillmentStatus fulfillmentStatus,
        @Schema(description = "주문 금액 합계(원 단위)") long totalAmount,
        @Schema(description = "할인 금액(원 단위)") long discountAmount,
        @Schema(description = "배송비(원 단위)") long shippingFee,
        @Schema(description = "결제 금액(원 단위)") long payAmount,

        @Schema(description = "적용된 발급 쿠폰 ID. 미적용이면 없음", nullable = true) @Nullable
        UUID issuedCouponId,

        @Schema(description = "배송지") AddressResponse shippingAddress,
        @Schema(description = "주문 라인 목록") List<OrderLineResponse> lines,
        @Schema(description = "주문 생성 시각") Instant createdAt,

        @Schema(description = "결제 완료 시각. 미결제면 없음", nullable = true) @Nullable
        Instant paidAt,

        @Schema(description = "출고 시각. 미출고면 없음", nullable = true) @Nullable
        Instant shippedAt,

        @Schema(description = "택배사. 미출고면 없음", nullable = true) @Nullable
        String carrier,

        @Schema(description = "운송장 번호. 미출고면 없음", nullable = true) @Nullable
        String trackingNumber,

        @Schema(description = "배송 완료 시각. 미완료면 없음", nullable = true) @Nullable
        Instant deliveredAt,

        @Schema(description = "취소 시각. 미취소면 없음", nullable = true) @Nullable
        Instant cancelledAt,

        @Schema(description = "취소 사유. 미취소면 없음", nullable = true) @Nullable
        CancellationReason cancellationReason,

        @Schema(description = "이행 보류 사유. 미보류면 없음", nullable = true) @Nullable
        HoldReason holdReason,

        @Schema(description = "환불 시각. 미환불이면 없음", nullable = true) @Nullable
        Instant refundedAt,

        @Schema(description = "환불 사유. 미환불이면 없음", nullable = true) @Nullable
        RefundReason refundReason) {

    public OrderResponse {
        lines = List.copyOf(lines);
    }

    /** 주문 조회 모델에서 응답을 만든다. 라인은 변형 ID 오름차순으로 싣는다. */
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
                order.carrier(),
                order.trackingNumber(),
                order.deliveredAt(),
                order.cancelledAt(),
                order.cancellationReason(),
                order.holdReason(),
                order.refundedAt(),
                order.refundReason());
    }
}
