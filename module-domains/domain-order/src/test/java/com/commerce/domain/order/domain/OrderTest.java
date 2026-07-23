package com.commerce.domain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.order.domain.exception.InvalidOrderException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderLineNotFoundException;
import com.commerce.domain.order.domain.exception.OrderStatusException;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");

    private OrderLineSnapshot line(long price, int quantity) {
        return new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(price), quantity);
    }

    private Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private Order place(Money discount, Money shippingFee, @org.jspecify.annotations.Nullable UUID couponId) {
        return Order.place(UUID.randomUUID(), List.of(line(10000L, 2)), address(), discount, shippingFee, couponId);
    }

    private Order paidOrder() {
        Order order = place(Money.ZERO, Money.of(3000L), null);
        order.markPaid(NOW);
        return order;
    }

    @Test
    @DisplayName("생성 시 금액을 자기 계산하고 PENDING이다")
    void placeComputesAmounts() {
        Order order = place(Money.ZERO, Money.of(3000L), null);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(20000L));
        assertThat(order.getPayAmount()).isEqualTo(Money.of(23000L));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderNumber()).isNotBlank();
    }

    @Test
    @DisplayName("쿠폰 할인이 있으면 payAmount에서 차감된다")
    void placeWithCoupon() {
        Order order = place(Money.of(5000L), Money.of(3000L), UUID.randomUUID());
        assertThat(order.getPayAmount()).isEqualTo(Money.of(18000L));
        assertThat(order.getIssuedCouponId()).isNotNull();
    }

    @Test
    @DisplayName("라인이 없으면 거부한다")
    void placeRejectsEmpty() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), List.of(), address(), Money.ZERO, Money.ZERO, null))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("할인이 주문 금액을 초과하면 거부한다")
    void placeRejectsDiscountExceedingTotal() {
        assertThatThrownBy(() -> place(Money.of(25000L), Money.ZERO, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("쿠폰과 할인액의 존재가 불일치하면 거부한다")
    void placeRejectsCouponDiscountMismatch() {
        assertThatThrownBy(() -> place(Money.ZERO, Money.ZERO, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> place(Money.of(5000L), Money.ZERO, null)).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("결제 완료는 PAID로 전이한다")
    void markPaidTransitions() {
        Order order = paidOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PENDING이 아니면 결제 완료할 수 없다")
    void markPaidRejectsNonPending() {
        assertThatThrownBy(() -> paidOrder().markPaid(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문에 재고 차감 완료를 기록한다")
    void markStockDeductedRecordsEvidence() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        assertThat(order.getStockDeductedAt()).isNull();
        order.markStockDeducted(NOW);
        assertThat(order.getStockDeductedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING이 아니면 재고 차감 완료를 기록할 수 없다")
    void markStockDeductedRejectsNonPending() {
        assertThatThrownBy(() -> paidOrder().markStockDeducted(NOW)).isInstanceOf(OrderStatusException.class);
        Order cancelled = place(Money.ZERO, Money.ZERO, null);
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.NOT_STARTED, NOW);
        assertThatThrownBy(() -> cancelled.markStockDeducted(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문을 취소한다")
    void cancelPendingOrder() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.NOT_STARTED, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("출고 이후 주문은 취소할 수 없다")
    void cancelRejectedAfterShipped() {
        Order order = paidOrder();
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("전액 할인이면 payAmount는 배송비뿐이고, 배송비도 0이면 payAmount는 0이다")
    void payAmountEdges() {
        assertThat(place(Money.of(20000L), Money.of(3000L), UUID.randomUUID()).getPayAmount())
                .isEqualTo(Money.of(3000L));
        assertThat(place(Money.of(20000L), Money.ZERO, UUID.randomUUID()).getPayAmount())
                .isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("결제 완료 주문은 준비·보류 상태에서 취소할 수 있다")
    void cancelPaidBeforeShip() {
        Order preparing = paidOrder();
        preparing.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThat(preparing.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order onHold = paidOrder();
        onHold.cancel(CancellationReason.STOCK_SHORTAGE, FulfillmentStatus.ON_HOLD, NOW);
        assertThat(onHold.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 개시는 마커를 남기고 재개시는 아무것도 하지 않는다")
    void requestCancellationSetsMarkerOnce() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.PREPARING, NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);

        order.requestCancellation(FulfillmentStatus.PREPARING, NOW.plusSeconds(60));
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("보류 중 주문도 취소를 개시할 수 있다")
    void requestCancellationAllowsOnHold() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.ON_HOLD, NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("미결제·취소된·출고된 주문은 취소를 개시할 수 없다")
    void requestCancellationRejectsIneligibleStates() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.requestCancellation(FulfillmentStatus.NOT_STARTED, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order cancelled = paidOrder();
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> cancelled.requestCancellation(FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order shipped = paidOrder();
        assertThatThrownBy(() -> shipped.requestCancellation(FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소 개시된 주문의 취소는 완결할 수 있다")
    void cancelCompletesWhileCancellationRequested() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.PREPARING, NOW);

        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("배송 완료 주문을 환불하면 REFUNDED가 된다")
    void refundDeliveredOrder() {
        Order order = paidOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAt()).isNotNull();
        assertThat(order.getRefundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
    }

    @Test
    @DisplayName("배송 완료 전(준비·출고) 주문은 환불할 수 없다")
    void refundRejectedBeforeDelivery() {
        assertThatThrownBy(() -> paidOrder().refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);
        assertThatThrownBy(() -> paidOrder().refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소된 주문은 환불할 수 없다")
    void refundRejectedForCancelledOrder() {
        Order order = paidOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불은 1회만 유효하고 환불된 주문은 취소도 할 수 없다")
    void refundIsOneShotAndBlocksCancel() {
        Order order = paidOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
        assertThatThrownBy(() -> order.cancel(CancellationReason.ADMIN_ACTION, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("배송 완료 결제 주문에 반품을 요청하면 REQUESTED와 사유·시각이 기록된다")
    void requestReturnRecordsRequest() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("배송 완료 아닌(준비·출고·미결제) 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsUndelivered() {
        assertThatThrownBy(
                        () -> paidOrder().requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_ALLOWED));

        assertThatThrownBy(() -> paidOrder().requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.NOT_STARTED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불 완료된 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsRefundedOrder() {
        Order order = paidOrder();
        order.refund(RefundReason.CS_MANUAL, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("반품 요청 중 재요청은 거부된다")
    void requestReturnRejectsDuplicate() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED));
    }

    @Test
    @DisplayName("반품 거절은 REJECTED로 전이하고 주문은 PAID로 남는다")
    void rejectReturnKeepsOrderState() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("요청 상태가 아니면 반품을 거절할 수 없다")
    void rejectReturnRequiresRequested() {
        Order order = paidOrder();
        assertThatThrownBy(order::rejectReturn)
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_REQUESTED));

        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        assertThatThrownBy(order::rejectReturn).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("거절 후 재요청은 허용된다")
    void requestReturnAllowedAfterRejection() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW.plusSeconds(60));
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("반품 요청된 주문이 환불되면 반품은 COMPLETED로 완결된다")
    void refundCompletesRequestedReturn() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    // 동일 금액 3라인(각 10,000)·할인 1,000·배송비 0 — 안분에 끝전(1000/3)이 생기는 결제 완료 주문.
    private Order paidThreeLineDiscountedOrder() {
        Order order = Order.place(
                UUID.randomUUID(),
                List.of(line(10000L, 1), line(10000L, 1), line(10000L, 1)),
                address(),
                Money.of(1000L),
                Money.ZERO,
                UUID.randomUUID());
        order.markPaid(NOW);
        return order;
    }

    private static List<UUID> lineIds(Order order) {
        return order.getLines().stream().map(OrderLine::getId).sorted().toList();
    }

    @Test
    @DisplayName("순차 전 라인 취소의 안분 할인 합계가 총할인과 일치하고 마지막 라인 환불이 결제 잔액 전액이다")
    void lineRefundsProrateDiscountExactly() {
        Order order = paidThreeLineDiscountedOrder();
        List<UUID> ids = lineIds(order);

        Money first = order.beginLineCancellation(ids.get(0), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(0), NOW);
        Money second = order.beginLineCancellation(ids.get(1), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(1), NOW);
        Money third = order.beginLineCancellation(ids.get(2), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(2), NOW);

        // 각 라인 할인 = 라인 금액 − 환불액. 내림 안분 333·333에 끝전 334가 마지막 잔액 환불로 흡수된다.
        assertThat(first).isEqualTo(Money.of(9667L));
        assertThat(second).isEqualTo(Money.of(9667L));
        assertThat(third).isEqualTo(Money.of(9666L));
        long discountSum = 30000L - first.amount() - second.amount() - third.amount();
        assertThat(discountSum).isEqualTo(1000L);
        assertThat(order.getRefundedAmount()).isEqualTo(order.getPayAmount());
    }

    @Test
    @DisplayName("부분 취소 후 잔여 환불 누계·라인 상태가 정확하고 주문은 결제 완료로 남는다")
    void beginAndCompleteCancelSingleLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        Money refund = order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        assertThat(refund).isEqualTo(Money.of(9667L));
        assertThat(order.getRefundedAmount()).isEqualTo(Money.of(9667L));
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.CANCELLING);
        assertThat(lineOf(order, lineId).getRefundAmount()).isEqualTo(Money.of(9667L));

        boolean converged = order.completeLineCancellation(lineId, NOW);

        assertThat(converged).isFalse();
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("마지막 잔여 라인 취소 완결 시 주문이 전체 취소로 수렴한다")
    void lastLineCancellationConvergesToFullCancellation() {
        Order order =
                Order.place(UUID.randomUUID(), List.of(line(10000L, 1)), address(), Money.ZERO, Money.of(3000L), null);
        order.markPaid(NOW);
        UUID lineId = lineIds(order).get(0);

        Money refund = order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        boolean converged = order.completeLineCancellation(lineId, NOW);

        assertThat(refund).isEqualTo(Money.of(13000L));
        assertThat(converged).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
    }

    @Test
    @DisplayName("라인 취소 개시는 결제 완료·출고 전 주문의 주문됨 라인에서만 허용된다")
    void beginLineCancellationGuards() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.beginLineCancellation(lineIds(pending).get(0), FulfillmentStatus.NOT_STARTED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        Order shipped = paidOrder();
        assertThatThrownBy(() -> shipped.beginLineCancellation(lineIds(shipped).get(0), FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        Order cancelRequested = paidOrder();
        cancelRequested.requestCancellation(FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> cancelRequested.beginLineCancellation(
                        lineIds(cancelRequested).get(0), FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS);

        Order order = paidThreeLineDiscountedOrder();
        assertThatThrownBy(() -> order.beginLineCancellation(UUID.randomUUID(), FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderLineNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_LINE_NOT_FOUND);

        UUID lineId = lineIds(order).get(0);
        order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        order.completeLineCancellation(lineId, NOW);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("취소 진행 중이 아닌 라인의 완결은 거부된다")
    void completeLineCancellationRequiresCancellingLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        assertThatThrownBy(() -> order.completeLineCancellation(lineId, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("부분 취소 이력이 있는 주문의 전체 취소 개시는 거부된다")
    void requestCancellationRejectsPartiallyCancelledOrder() {
        Order order = paidThreeLineDiscountedOrder();
        order.beginLineCancellation(lineIds(order).get(0), FulfillmentStatus.PREPARING);

        assertThatThrownBy(() -> order.requestCancellation(FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("불균등 금액·복수 수량 라인의 안분 합계도 총할인과 일치한다")
    void unevenLineRefundsProrateDiscountExactly() {
        // 7000×1 + 6000×2 + 3100×1 = 22100, 할인 1000 — 안분 316·542에 끝전이 남는 조합.
        Order order = Order.place(
                UUID.randomUUID(),
                List.of(line(7000L, 1), line(6000L, 2), line(3100L, 1)),
                address(),
                Money.of(1000L),
                Money.ZERO,
                UUID.randomUUID());
        order.markPaid(NOW);
        List<UUID> ids = lineIds(order);

        long refundSum = 0;
        for (UUID lineId : ids) {
            refundSum += order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING)
                    .amount();
            order.completeLineCancellation(lineId, NOW);
        }

        assertThat(refundSum).isEqualTo(order.getPayAmount().amount());
        assertThat(22100L - refundSum).isEqualTo(1000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("부분 취소 이력이 있는 주문의 전체 반품 환불은 거부된다")
    void refundRejectsPartiallyCancelledOrder() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);
        order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        order.completeLineCancellation(lineId, NOW);

        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("라인 반품 요청·승인이 공유 산식으로 환불액을 확정하고 라인을 RETURNED로 완결한다")
    void lineReturnRequestAndApprovalConfirmsRefund() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);

        Money refund = order.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
        assertThat(refund).isEqualTo(Money.of(9667L));
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURNING);
        assertThat(order.getRefundedAmount()).isEqualTo(Money.of(9667L));

        boolean converged = order.completeLineReturn(lineId, NOW);

        assertThat(converged).isFalse();
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURNED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("라인 반품 요청은 배송 완료된 주문의 주문됨 라인에서만 허용된다")
    void requestLineReturnGuards() {
        Order paid = paidThreeLineDiscountedOrder();
        assertThatThrownBy(() -> paid.requestLineReturn(
                        lineIds(paid).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_NOT_ALLOWED);

        Order order = paidThreeLineDiscountedOrder();
        UUID returned = lineIds(order).get(0);
        order.requestLineReturn(returned, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        order.beginLineReturn(returned, FulfillmentStatus.DELIVERED);
        order.completeLineReturn(returned, NOW);
        assertThatThrownBy(() ->
                        order.requestLineReturn(returned, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        UUID requested = lineIds(order).get(1);
        order.requestLineReturn(requested, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() ->
                        order.requestLineReturn(requested, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        Order mixed = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(mixed).get(0);
        mixed.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        mixed.completeLineCancellation(cancelled, NOW);
        assertThatThrownBy(() ->
                        mixed.requestLineReturn(cancelled, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        Order fullReturn = paidThreeLineDiscountedOrder();
        fullReturn.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> fullReturn.requestLineReturn(
                        lineIds(fullReturn).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("전 라인 반품 완결 시 주문이 REFUNDED로 수렴하고 혼합(취소+반품) 종결도 REFUNDED다")
    void allLineReturnsConvergeToRefunded() {
        Order order = paidThreeLineDiscountedOrder();
        for (UUID lineId : lineIds(order)) {
            order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
            order.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
            order.completeLineReturn(lineId, NOW);
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAmount()).isEqualTo(order.getPayAmount());
        assertThat(order.getRefundedAt()).isEqualTo(NOW);
        assertThat(order.getRefundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);

        Order mixed = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(mixed).get(0);
        mixed.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        mixed.completeLineCancellation(cancelled, NOW);
        for (UUID lineId : List.of(lineIds(mixed).get(1), lineIds(mixed).get(2))) {
            mixed.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);
            mixed.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
            mixed.completeLineReturn(lineId, NOW);
        }
        assertThat(mixed.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(mixed.getRefundedAmount()).isEqualTo(mixed.getPayAmount());
    }

    @Test
    @DisplayName("라인 반품 거절은 라인을 주문됨으로 되돌리고 재요청이 가능하다")
    void rejectLineReturnRestoresOrderedLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);
        order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);

        order.rejectLineReturn(lineId);

        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.ORDERED);
        assertThat(lineOf(order, lineId).getReturnReason()).isNull();
        order.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
    }

    @Test
    @DisplayName("전체 반품 요청은 부분 환불 이력·라인 반품 진행 중이면 거부된다")
    void requestReturnRejectsPartialHistory() {
        Order partiallyCancelled = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(partiallyCancelled).get(0);
        partiallyCancelled.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        partiallyCancelled.completeLineCancellation(cancelled, NOW);
        assertThatThrownBy(() ->
                        partiallyCancelled.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);

        Order lineReturning = paidThreeLineDiscountedOrder();
        lineReturning.requestLineReturn(
                lineIds(lineReturning).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() ->
                        lineReturning.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("라인 이력이 있는 주문의 전체 반품 환불 집행은 거부된다")
    void refundRejectsAnyLineHistory() {
        Order requested = paidThreeLineDiscountedOrder();
        requested.requestLineReturn(
                lineIds(requested).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() -> requested.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("라인 반품 승인 개시는 배송 완료된 결제 주문·전체 반품 미진행에서만 허용된다")
    void beginLineReturnGuardsOrderState() {
        // 반품 요청 라인을 만든 뒤 주문 상태가 어긋난 경우를 재현할 수 없으므로(요청 가드가 선행),
        // 전체 반품 요청과의 공존(쓰기 스큐 잔여 상태)을 직접 재현해 집행 가드를 단언한다.
        Order order = paidThreeLineDiscountedOrder();
        order.requestLineReturn(lineIds(order).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        forceFullReturnRequested(order);

        assertThatThrownBy(() -> order.beginLineReturn(lineIds(order).get(0), FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("전체 반품 거절 후 라인 반품 요청은 허용된다")
    void lineReturnAllowedAfterFullReturnRejection() {
        Order order = paidThreeLineDiscountedOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();

        UUID lineId = lineIds(order).get(0);
        order.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);

        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
    }

    /** 쓰기 스큐로만 도달 가능한 전체 반품 요청 공존 상태를 리플렉션으로 재현한다. */
    private static void forceFullReturnRequested(Order order) {
        try {
            var field = Order.class.getDeclaredField("returnRequest");
            field.setAccessible(true);
            field.set(order, new ReturnRequest(ReturnStatus.REQUESTED, NOW, RefundReason.PRODUCT_DEFECT));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OrderLine lineOf(Order order, UUID lineId) {
        return order.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow();
    }
}
