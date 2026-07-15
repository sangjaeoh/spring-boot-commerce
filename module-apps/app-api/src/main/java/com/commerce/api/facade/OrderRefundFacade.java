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

/**
 * 배송 완료 주문의 전체 반품·전액 환불 흐름을 조율한다. 관리자 액션이다.
 *
 * <p>취소 파사드와 같은 이중 가드다. 결제 취소(환불)를 먼저 하고, 성공 시 주문 환불의 1회성 전이가
 * 재고·쿠폰 복원을 게이트한다. 환불이 복원의 선행조건이라 환불 실패 시 주문은 PAID로 남고 복원하지 않는다.
 * 재고 복원은 반품 상품의 회수·재판매를 가정하고, 쿠폰 복원은 사용 기한 경과분이면 명목 복원이다(사용
 * 시점 검증이 거부).
 *
 * <p>환불 커밋 후 다운스트림(주문 환불·복원)이 실패해 재시도되면, 이미 REFUNDED·CANCELLED된 주문·결제를
 * 관용해 복원을 완결한다. 멱등 쿠폰 복원을 비멱등 가산 재고 복원 앞에 두어 재고를 종단 단계로 만든다.
 * 결제 취소가 CANCELLED 결제를 PG 재호출 없이 통과시켜 환불은 최대 한 번만 일어난다.
 */
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
     * 배송 완료 주문을 전체 반품 환불하고 재고·쿠폰을 복원한다.
     *
     * @throws ApiException 환불할 수 없는 주문 상태면
     */
    public void refund(UUID orderId, RefundReason reason) {
        OrderInfo order = orderReader.getOrder(orderId);
        requireRefundable(order);

        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancel(payment.id());
        if (order.status() == OrderStatus.PAID) {
            orderModifier.refund(orderId, reason);
        }

        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId);
        }
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }

    private void requireRefundable(OrderInfo order) {
        if (order.status() == OrderStatus.REFUNDED) {
            return;
        }
        boolean refundable =
                order.status() == OrderStatus.PAID && order.fulfillmentStatus() == FulfillmentStatus.DELIVERED;
        if (!refundable) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_REFUNDABLE);
        }
    }
}
