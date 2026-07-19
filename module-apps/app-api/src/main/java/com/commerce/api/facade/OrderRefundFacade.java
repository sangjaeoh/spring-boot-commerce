package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.info.OrderLineInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.service.PaymentProcessor;
import com.commerce.payment.service.PaymentReader;
import com.commerce.stock.service.StockModifier;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 배송 완료 주문의 전체 반품·전액 환불 흐름을 조율한다. 관리자 액션이다. */
@Component
public class OrderRefundFacade {

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final PaymentProcessor paymentProcessor;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;

    public OrderRefundFacade(
            OrderReader orderReader,
            PaymentReader paymentReader,
            PaymentProcessor paymentProcessor,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier) {
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.paymentProcessor = paymentProcessor;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
    }

    /**
     * 배송 완료 주문을 전체 반품 환불하고 재고·쿠폰을 복원한다. 이미 환불된 주문은 복원 없이 통과한다.
     *
     * <p>환불이 복원의 선행조건이라 환불에 실패하면 주문은 PAID로 남고 복원하지 않는다.
     *
     * @throws ApiException 환불할 수 없는 주문 상태면
     */
    public void refund(UUID orderId, RefundReason reason) {
        // 1. 주문 조회 — 이미 환불됐으면 복원 없이 통과
        OrderInfo order = orderReader.getOrder(orderId);
        if (order.status() == OrderStatus.REFUNDED) {
            return;
        }
        // 2. 환불 가능 상태 확인
        requireRefundable(order);

        // 3. 결제 취소(환불)
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancel(payment.id());
        // 4. 주문 환불 전이 — 1회성이라 복원이 정확히 한 번이다
        orderModifier.refund(orderId, reason);

        // 5. 쿠폰 복원
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        // 6. 재고 복원
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }

    /** 결제 완료이면서 배송 완료된 주문만 통과시킨다. */
    private void requireRefundable(OrderInfo order) {
        boolean refundable =
                order.status() == OrderStatus.PAID && order.fulfillmentStatus() == FulfillmentStatus.DELIVERED;
        if (!refundable) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_REFUNDABLE);
        }
    }
}
