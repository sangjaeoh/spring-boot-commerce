package com.commerce.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.core.money.Money;
import com.commerce.order.exception.FulfillmentStatusException;
import com.commerce.order.exception.InvalidOrderException;
import com.commerce.order.exception.OrderStatusException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

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
        order.markPaid();
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
        assertThatThrownBy(paidOrder()::markPaid).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문을 취소한다")
    void cancelPendingOrder() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("출고 이후 주문은 취소할 수 없다")
    void cancelRejectedAfterShipped() {
        Order order = paidOrder();
        order.ship();
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST))
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
        preparing.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(preparing.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order onHold = paidOrder();
        onHold.holdFulfillment(HoldReason.STOCK_DELAY);
        onHold.cancel(CancellationReason.STOCK_SHORTAGE);
        assertThat(onHold.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이행 전진은 결제 완료에서만 유효하다")
    void fulfillmentRequiresPaid() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(pending::ship).isInstanceOf(FulfillmentStatusException.class);
    }

    @Test
    @DisplayName("준비→출고→배송 완료로 이행한다")
    void fulfillmentFlow() {
        Order order = paidOrder();
        order.ship();
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        order.confirmDelivery();
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("보류와 해제를 오간다. 보류 중에는 출고할 수 없다")
    void holdAndRelease() {
        Order order = paidOrder();
        order.holdFulfillment(HoldReason.FRAUD_REVIEW);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ON_HOLD);
        assertThatThrownBy(order::ship).isInstanceOf(FulfillmentStatusException.class);
        order.releaseFulfillment();
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
    }
}
