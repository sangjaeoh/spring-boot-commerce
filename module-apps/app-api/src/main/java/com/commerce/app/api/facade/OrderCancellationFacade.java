package com.commerce.app.api.facade;

import com.commerce.app.api.exception.ApiErrorCode;
import com.commerce.app.api.exception.ApiException;
import com.commerce.domain.coupon.application.provided.IssuedCouponModifier;
import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.info.OrderLineInfo;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderLineStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderLineNotFoundException;
import com.commerce.domain.payment.application.info.PaymentInfo;
import com.commerce.domain.payment.application.provided.PaymentProcessor;
import com.commerce.domain.payment.application.provided.PaymentReader;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockModifier;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 결제 완료 주문의 사용자 취소·환불 흐름을 조율하는 파사드다. */
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
     * <p>환불이 복원의 선행조건이라 환불에 실패하면 주문은 PAID로 남고 복원하지 않는다.
     *
     * @throws ApiException 취소할 수 없는 주문 상태면
     */
    public void cancel(UUID orderId, UUID memberId) {
        // 1. 본인 주문 조회 — 이미 취소됐으면 복원 없이 통과
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        if (order.status() == OrderStatus.CANCELLED) {
            return;
        }
        // 2. 취소 가능 상태 확인
        requireCancellable(order);

        // 3. 취소 개시 마커
        // 환불 앞에 커밋해 취소 진행 중 출고를 거부한다
        orderModifier.requestCancellation(orderId);

        // 4. 결제 취소(환불)
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancel(payment.id());
        // 5. 주문 취소 전이
        // 1회성이라 복원이 정확히 한 번이다
        orderModifier.cancel(orderId, CancellationReason.CUSTOMER_REQUEST);

        // 6. 쿠폰 복원
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        // 7. 재고 복원
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }

    /**
     * 회원 본인 주문의 한 라인을 취소하고 부분 환불·재고를 복원한다. 마지막 라인이면 주문이 전체 취소로
     * 수렴하고 쿠폰을 복원한다. 이미 취소된 라인은 복원 없이 통과하고, 취소 진행 중 라인은 환불부터 재개한다.
     *
     * @throws ApiException 취소할 수 없는 주문 상태면
     */
    public void cancelLine(UUID orderId, UUID lineId, UUID memberId) {
        // 1. 본인 주문·라인 조회
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        OrderLineInfo line = order.lines().stream()
                .filter(candidate -> candidate.id().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new OrderLineNotFoundException(OrderErrorCode.ORDER_LINE_NOT_FOUND));
        if (line.status() == OrderLineStatus.CANCELLED) {
            return;
        }

        // 2. 취소 개시 — 환불액 확정·경합 직렬화. 진행 중 라인은 기록된 환불액으로 재개한다
        Money refund;
        if (line.status() == OrderLineStatus.ORDERED) {
            requireCancellable(order);
            refund = orderModifier.beginLineCancellation(orderId, lineId);
        } else {
            refund = Objects.requireNonNull(line.refundAmount());
        }

        // 3. 결제 부분 환불(트랜잭션 밖, 라인 멱등 키 + 주문 측 확정 누계 동기화)
        Money refundedTotal = orderReader.getOrder(orderId, memberId).refundedAmount();
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancelPartially(payment.id(), refund, refundedTotal, lineId);

        // 4. 라인 취소 완결 — 마지막 라인이면 전체 취소로 수렴
        boolean converged = orderModifier.completeLineCancellation(orderId, lineId);

        // 5. 그 라인 재고만 복원
        stockModifier.restore(line.variantId(), line.quantity());

        // 6. 전량 취소 수렴 시에만 쿠폰 복원
        UUID issuedCouponId = order.issuedCouponId();
        if (converged && issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
    }

    /** 결제 완료이면서 출고 전(준비 중·보류)인 주문만 통과시킨다. */
    private void requireCancellable(OrderInfo order) {
        boolean cancellable = order.status() == OrderStatus.PAID
                && (order.fulfillmentStatus() == FulfillmentStatus.PREPARING
                        || order.fulfillmentStatus() == FulfillmentStatus.ON_HOLD);
        if (!cancellable) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_CANCELLABLE);
        }
    }
}
