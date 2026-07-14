package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderStatus;
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
 * 결제 완료 주문의 사용자 취소·환불 흐름을 조율한다.
 *
 * <p>이중 가드다. 결제 취소(환불)를 먼저 하고, 성공 시 주문 취소의 1회성 전이가 재고·쿠폰 복원을
 * 게이트한다. 환불이 복원의 선행조건이라 환불 실패 시 주문은 PAID로 남고 복원하지 않는다.
 */
@Component
public class OrderCancellationFacade {

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final PaymentProcessor paymentProcessor;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;

    public OrderCancellationFacade(
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
     * 주문을 취소하고 환불·재고·쿠폰을 복원한다.
     *
     * @throws ApiException 취소할 수 없는 주문 상태면
     */
    public void cancel(UUID orderId) {
        OrderInfo order = orderReader.getOrder(orderId);
        requireCancellable(order);

        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancel(payment.id());
        orderModifier.cancel(orderId, CancellationReason.CUSTOMER_REQUEST);

        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId);
        }
    }

    private void requireCancellable(OrderInfo order) {
        boolean cancellable = order.status() == OrderStatus.PAID
                && (order.fulfillmentStatus() == FulfillmentStatus.PREPARING
                        || order.fulfillmentStatus() == FulfillmentStatus.ON_HOLD);
        if (!cancellable) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_CANCELLABLE);
        }
    }
}
