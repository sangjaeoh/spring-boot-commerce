package com.commerce.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.core.money.Money;
import com.commerce.order.exception.FulfillmentStatusException;
import com.commerce.order.exception.InvalidOrderException;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderStatusException;
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
}
