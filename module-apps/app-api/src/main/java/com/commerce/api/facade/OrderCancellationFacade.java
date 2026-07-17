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
 *
 * <p>이중 가드에 앞서 취소 개시 마커({@code Order.requestCancellation})를 PG 환불 앞에 커밋해 취소 진행 중
 * 주문의 출고를 거부한다. 마커 커밋은 주문 낙관락으로 {@code ship}과 직렬화되므로 출고가 먼저 커밋되면
 * 취소가 환불 전에 중단되고, 마커가 먼저 커밋되면 출고가 거부된다 — 환불 커밋과 주문 전이 사이에 출고가
 * 끼어들어 남던 환불 고아(결제 CANCELLED × 주문 PAID+SHIPPED)가 없다. 마커 커밋 후 중단된 취소는 이
 * 파사드의 재시도가 완결한다(마커 재개시는 no-op, 출고가 차단돼 재시도가 SHIPPED에 막히지 않는다).
 *
 * <p>복원은 주문 취소 전이가 이 호출에서 실제 일어났을 때만 탄다. 이미 CANCELLED인 주문은 복원 재실행 없이
 * 관용 통과시켜 재호출이 재고를 가산 증식시키거나 다른 주문에 재사용된 쿠폰을 풀지 못한다. 환불 커밋 후 주문
 * 취소가 실패한 재시도는 이미 CANCELLED인 결제를 관용해 취소·복원을 완결한다. 취소 커밋과 복원 사이 중단의
 * 복원 유실은 잔여 한계다(DOMAIN_MODEL.md 취소·환불 정책 참조). 쿠폰 복원을 재고 복원 앞에 둔다.
 *
 * <p>동시 취소 2건이 겹쳐 둘 다 전이 가드를 통과해도 주문·결제 낙관락({@code @Version})이 한쪽만 커밋시켜
 * 복원이 정확히 한 번이다. 진 쪽의 충돌은 409로 응답한다(클라이언트 재시도, 재시도는 관용 통과).
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
     * 회원 본인의 주문을 취소하고 환불·재고·쿠폰을 복원한다. 타인 주문은 미존재로 취급하고, 이미 취소된
     * 주문은 복원 없이 통과한다.
     *
     * @throws ApiException 취소할 수 없는 주문 상태면
     */
    public void cancel(UUID orderId, UUID memberId) {
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        if (order.status() == OrderStatus.CANCELLED) {
            return;
        }
        requireCancellable(order);

        orderModifier.requestCancellation(orderId);

        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancel(payment.id());
        orderModifier.cancel(orderId, CancellationReason.CUSTOMER_REQUEST);

        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
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
