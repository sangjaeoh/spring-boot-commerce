package com.commerce.domain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
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
    private static final String CARRIER = "CJ대한통운";
    private static final String TRACKING_NUMBER = "688900123456";

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
    @DisplayName("생성 시 금액을 자기 계산하고 PENDING·NOT_STARTED다")
    void placeComputesAmounts() {
        Order order = place(Money.ZERO, Money.of(3000L), null);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(20000L));
        assertThat(order.getPayAmount()).isEqualTo(Money.of(23000L));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NOT_STARTED);
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
    @DisplayName("결제 완료는 PAID로, 이행을 준비 중으로 전진시킨다")
    void markPaidTransitions() {
        Order order = paidOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
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
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThatThrownBy(() -> cancelled.markStockDeducted(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문을 취소한다")
    void cancelPendingOrder() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("출고 이후 주문은 취소할 수 없다")
    void cancelRejectedAfterShipped() {
        Order order = paidOrder();
        order.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW))
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
        preparing.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThat(preparing.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order onHold = paidOrder();
        onHold.holdFulfillment(HoldReason.STOCK_DELAY);
        onHold.cancel(CancellationReason.STOCK_SHORTAGE, NOW);
        assertThat(onHold.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 개시는 마커를 남기고 재개시는 아무것도 하지 않는다")
    void requestCancellationSetsMarkerOnce() {
        Order order = paidOrder();
        order.requestCancellation(NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);

        order.requestCancellation(NOW.plusSeconds(60));
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("보류 중 주문도 취소를 개시할 수 있다")
    void requestCancellationAllowsOnHold() {
        Order order = paidOrder();
        order.holdFulfillment(HoldReason.STOCK_DELAY);
        order.requestCancellation(NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("미결제·취소된·출고된 주문은 취소를 개시할 수 없다")
    void requestCancellationRejectsIneligibleStates() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.requestCancellation(NOW)).isInstanceOf(OrderStatusException.class);

        Order cancelled = paidOrder();
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThatThrownBy(() -> cancelled.requestCancellation(NOW)).isInstanceOf(OrderStatusException.class);

        Order shipped = paidOrder();
        shipped.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThatThrownBy(() -> shipped.requestCancellation(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소 개시된 주문은 출고할 수 없고 취소는 완결할 수 있다")
    void shipRejectedWhileCancellationInProgress() {
        Order order = paidOrder();
        order.requestCancellation(NOW);

        assertThatThrownBy(() -> order.ship(CARRIER, TRACKING_NUMBER, NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));

        order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private Order deliveredOrder() {
        Order order = paidOrder();
        order.ship(CARRIER, TRACKING_NUMBER, NOW);
        order.confirmDelivery(NOW);
        return order;
    }

    @Test
    @DisplayName("배송 완료 주문을 환불하면 REFUNDED가 되고 이행 축은 DELIVERED로 남는다")
    void refundDeliveredOrder() {
        Order order = deliveredOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.getRefundedAt()).isNotNull();
        assertThat(order.getRefundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
    }

    @Test
    @DisplayName("배송 완료 전(준비·출고) 주문은 환불할 수 없다")
    void refundRejectedBeforeDelivery() {
        assertThatThrownBy(() -> paidOrder().refund(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order shipped = paidOrder();
        shipped.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThatThrownBy(() -> shipped.refund(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소된 주문은 환불할 수 없다")
    void refundRejectedForCancelledOrder() {
        Order order = paidOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불은 1회만 유효하고 환불된 주문은 취소도 할 수 없다")
    void refundIsOneShotAndBlocksCancel() {
        Order order = deliveredOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, NOW))
                .isInstanceOf(OrderStatusException.class);
        assertThatThrownBy(() -> order.cancel(CancellationReason.ADMIN_ACTION, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("이행 전진은 결제 완료에서만 유효하다")
    void fulfillmentRequiresPaid() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.ship(CARRIER, TRACKING_NUMBER, NOW))
                .isInstanceOf(FulfillmentStatusException.class);
    }

    @Test
    @DisplayName("준비→출고→배송 완료로 이행한다")
    void fulfillmentFlow() {
        Order order = paidOrder();
        order.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        order.confirmDelivery(NOW);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("출고 시 택배사·운송장 번호를 기록하고 출고 전에는 null이다")
    void shipRecordsTrackingInfo() {
        Order order = paidOrder();
        assertThat(order.getCarrier()).isNull();
        assertThat(order.getTrackingNumber()).isNull();

        order.ship(CARRIER, TRACKING_NUMBER, NOW);

        assertThat(order.getCarrier()).isEqualTo(CARRIER);
        assertThat(order.getTrackingNumber()).isEqualTo(TRACKING_NUMBER);
    }

    @Test
    @DisplayName("보류와 해제를 오간다. 보류 중에는 출고할 수 없다")
    void holdAndRelease() {
        Order order = paidOrder();
        order.holdFulfillment(HoldReason.FRAUD_REVIEW);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ON_HOLD);
        assertThatThrownBy(() -> order.ship(CARRIER, TRACKING_NUMBER, NOW))
                .isInstanceOf(FulfillmentStatusException.class);
        order.releaseFulfillment();
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
    }

    @Test
    @DisplayName("배송 완료 결제 주문에 반품을 요청하면 REQUESTED와 사유·시각이 기록된다")
    void requestReturnRecordsRequest() {
        Order order = deliveredOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, NOW);
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("배송 완료 아닌(준비·출고·미결제) 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsUndelivered() {
        assertThatThrownBy(() -> paidOrder().requestReturn(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_ALLOWED));

        Order shipped = paidOrder();
        shipped.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThatThrownBy(() -> shipped.requestReturn(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.requestReturn(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불 완료된 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsRefundedOrder() {
        Order order = deliveredOrder();
        order.refund(RefundReason.CS_MANUAL, NOW);
        assertThatThrownBy(() -> order.requestReturn(RefundReason.CHANGE_OF_MIND, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("반품 요청 중 재요청은 거부된다")
    void requestReturnRejectsDuplicate() {
        Order order = deliveredOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, NOW);
        assertThatThrownBy(() -> order.requestReturn(RefundReason.PRODUCT_DEFECT, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED));
    }

    @Test
    @DisplayName("반품 거절은 REJECTED로 전이하고 주문은 PAID·DELIVERED로 남는다")
    void rejectReturnKeepsOrderState() {
        Order order = deliveredOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, NOW);
        order.rejectReturn();
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("요청 상태가 아니면 반품을 거절할 수 없다")
    void rejectReturnRequiresRequested() {
        Order order = deliveredOrder();
        assertThatThrownBy(order::rejectReturn)
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_REQUESTED));

        order.requestReturn(RefundReason.CHANGE_OF_MIND, NOW);
        order.rejectReturn();
        assertThatThrownBy(order::rejectReturn).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("거절 후 재요청은 허용된다")
    void requestReturnAllowedAfterRejection() {
        Order order = deliveredOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, NOW);
        order.rejectReturn();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, NOW.plusSeconds(60));
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("반품 요청된 주문이 환불되면 반품은 COMPLETED로 완결된다")
    void refundCompletesRequestedReturn() {
        Order order = deliveredOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, NOW);
        order.refund(RefundReason.PRODUCT_DEFECT, NOW);
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

        Money first = order.beginLineCancellation(ids.get(0));
        order.completeLineCancellation(ids.get(0), NOW);
        Money second = order.beginLineCancellation(ids.get(1));
        order.completeLineCancellation(ids.get(1), NOW);
        Money third = order.beginLineCancellation(ids.get(2));
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

        Money refund = order.beginLineCancellation(lineId);
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

        Money refund = order.beginLineCancellation(lineId);
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
        // 미결제(PENDING) 주문 거부
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.beginLineCancellation(lineIds(pending).get(0)))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        // 출고 이후 거부
        Order shipped = paidOrder();
        shipped.ship(CARRIER, TRACKING_NUMBER, NOW);
        assertThatThrownBy(() -> shipped.beginLineCancellation(lineIds(shipped).get(0)))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        // 전체 취소 진행 중 거부
        Order cancelRequested = paidOrder();
        cancelRequested.requestCancellation(NOW);
        assertThatThrownBy(() -> cancelRequested.beginLineCancellation(
                        lineIds(cancelRequested).get(0)))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS);

        // 미존재 라인 거부
        Order order = paidThreeLineDiscountedOrder();
        assertThatThrownBy(() -> order.beginLineCancellation(UUID.randomUUID()))
                .isInstanceOf(OrderLineNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_LINE_NOT_FOUND);

        // 취소 진행 중·취소된 라인 재개시 거부
        UUID lineId = lineIds(order).get(0);
        order.beginLineCancellation(lineId);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        order.completeLineCancellation(lineId, NOW);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId))
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
        order.beginLineCancellation(lineIds(order).get(0));

        assertThatThrownBy(() -> order.requestCancellation(NOW))
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
            refundSum += order.beginLineCancellation(lineId).amount();
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
        order.beginLineCancellation(lineId);
        order.completeLineCancellation(lineId, NOW);
        order.ship(CARRIER, TRACKING_NUMBER, NOW);
        order.confirmDelivery(NOW);

        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("취소 진행 중 라인이 있는 주문의 출고는 거부된다")
    void shipRejectsWhileLineCancellationInProgress() {
        Order order = paidThreeLineDiscountedOrder();
        order.beginLineCancellation(lineIds(order).get(0));

        assertThatThrownBy(() -> order.ship(CARRIER, TRACKING_NUMBER, NOW))
                .isInstanceOf(FulfillmentStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS);
    }

    private static OrderLine lineOf(Order order, UUID lineId) {
        return order.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow();
    }
}
