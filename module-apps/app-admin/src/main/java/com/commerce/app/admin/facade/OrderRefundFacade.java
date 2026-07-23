package com.commerce.app.admin.facade;

import com.commerce.app.admin.exception.AdminErrorCode;
import com.commerce.app.admin.exception.AdminException;
import com.commerce.domain.coupon.application.provided.IssuedCouponModifier;
import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.info.OrderLineInfo;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderLineStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.ReturnStatus;
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

/** 배송 완료 주문의 전체 반품·전액 환불 흐름을 조율하는 파사드다. 관리자 액션이다. */
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
     * @throws AdminException 환불할 수 없는 주문 상태면
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
        // 4. 주문 환불 전이
        // 1회성이라 복원이 정확히 한 번이다
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

    /**
     * 반품 요청을 승인해 요청 사유로 환불을 완결하고 재고·쿠폰을 복원한다. 이미 완료된 반품은 멱등 통과한다.
     *
     * @throws AdminException 반품 요청 상태의 주문이 아니면
     */
    public void approveReturn(UUID orderId) {
        OrderInfo order = orderReader.getOrder(orderId);
        if (order.returnStatus() == ReturnStatus.COMPLETED) {
            return;
        }
        RefundReason reason = order.returnReason();
        if (order.returnStatus() != ReturnStatus.REQUESTED || reason == null) {
            throw new AdminException(AdminErrorCode.ORDER_RETURN_NOT_REQUESTED);
        }
        refund(orderId, reason);
    }

    /**
     * 라인 반품 요청을 승인해 부분 환불을 완결하고 그 라인 재고를 복원한다. 전 라인 종결로 주문이 환불에
     * 수렴하면 쿠폰을 복원한다. 이미 반품된 라인은 복원 없이 통과하고, 반품 진행 중 라인은 환불부터 재개한다.
     *
     * @throws AdminException 라인 반품을 승인할 수 없는 상태면
     */
    public void approveLineReturn(UUID orderId, UUID lineId) {
        // 1. 주문·라인 조회
        OrderInfo order = orderReader.getOrder(orderId);
        OrderLineInfo line = order.lines().stream()
                .filter(candidate -> candidate.id().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new OrderLineNotFoundException(OrderErrorCode.ORDER_LINE_NOT_FOUND));
        if (line.status() == OrderLineStatus.RETURNED) {
            return;
        }

        // 2. 승인 개시 — 환불액 확정·경합 직렬화. 진행 중 라인은 기록된 환불액으로 재개한다
        Money refund;
        if (line.status() == OrderLineStatus.RETURN_REQUESTED) {
            refund = orderModifier.beginLineReturn(orderId, lineId);
        } else if (line.status() == OrderLineStatus.RETURNING) {
            refund = Objects.requireNonNull(line.refundAmount());
        } else {
            throw new AdminException(AdminErrorCode.ORDER_RETURN_NOT_REQUESTED);
        }

        // 3. 결제 부분 환불(트랜잭션 밖, 라인 멱등 키 + 주문 측 확정 누계 동기화)
        Money refundedTotal = orderReader.getOrder(orderId).refundedAmount();
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        paymentProcessor.cancelPartially(payment.id(), refund, refundedTotal, lineId);

        // 4. 라인 반품 완결 — 전 라인 종결이면 환불 수렴
        boolean converged = orderModifier.completeLineReturn(orderId, lineId);

        // 5. 그 라인 재고만 복원
        stockModifier.restore(line.variantId(), line.quantity());

        // 6. 전량 종결 수렴 시에만 쿠폰 복원
        UUID issuedCouponId = order.issuedCouponId();
        if (converged && issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
    }

    /**
     * 라인 반품 요청을 거절한다. 라인은 주문됨으로 돌아간다.
     *
     * @throws AdminException 라인이 반품 요청 상태가 아니면
     */
    public void rejectLineReturn(UUID orderId, UUID lineId) {
        orderModifier.rejectLineReturn(orderId, lineId);
    }

    /** 결제 완료이면서 배송 완료된 주문만 통과시킨다. */
    private void requireRefundable(OrderInfo order) {
        boolean refundable =
                order.status() == OrderStatus.PAID && order.fulfillmentStatus() == FulfillmentStatus.DELIVERED;
        if (!refundable) {
            throw new AdminException(AdminErrorCode.ORDER_NOT_REFUNDABLE);
        }
    }
}
