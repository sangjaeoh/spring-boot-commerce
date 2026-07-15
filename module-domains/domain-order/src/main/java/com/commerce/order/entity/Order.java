package com.commerce.order.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.order.exception.FulfillmentStatusException;
import com.commerce.order.exception.InvalidOrderException;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderStatusException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 주문 애그리거트 루트다. 라인·배송지를 주문 시점 스냅샷으로 보관한다.
 *
 * <p>결제 축({@code status})과 이행 축({@code fulfillmentStatus})은 직교하며, 모든 이행 전진은
 * {@code status == PAID}에서만 유효하다. 최초 상태는 {@code PENDING}·{@code NOT_STARTED}다.
 */
@Entity
@Table(schema = "ordering", name = "orders")
public class Order extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "member_id")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status")
    private FulfillmentStatus fulfillmentStatus;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount")
    private Money totalAmount;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount")
    private Money discountAmount;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "shipping_fee")
    private Money shippingFee;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "pay_amount")
    private Money payAmount;

    @Column(name = "issued_coupon_id")
    @Nullable
    private UUID issuedCouponId;

    @Embedded
    private Address shippingAddress;

    @Column(name = "shipped_at")
    @Nullable
    private Instant shippedAt;

    @Column(name = "delivered_at")
    @Nullable
    private Instant deliveredAt;

    @Column(name = "paid_at")
    @Nullable
    private Instant paidAt;

    @Column(name = "cancelled_at")
    @Nullable
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    @Nullable
    private CancellationReason cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason")
    @Nullable
    private HoldReason holdReason;

    @Column(name = "refunded_at")
    @Nullable
    private Instant refundedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_reason")
    @Nullable
    private RefundReason refundReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderLine> lines = new HashSet<>();

    protected Order() {}

    private Order(UUID id, String orderNumber, UUID memberId, Address shippingAddress, Money shippingFee) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
        this.shippingFee = shippingFee;
        this.status = OrderStatus.PENDING;
        this.fulfillmentStatus = FulfillmentStatus.NOT_STARTED;
    }

    /**
     * 주문을 생성한다. totalAmount를 자기 계산하고 할인·payAmount 불변식을 자기 강제한다.
     *
     * @throws InvalidOrderException 라인이 없거나, 할인이 주문 금액을 초과하거나, 쿠폰과 할인액이 불일치하면
     */
    public static Order place(
            UUID memberId,
            List<OrderLineSnapshot> lineSnapshots,
            Address shippingAddress,
            Money discountAmount,
            Money shippingFee,
            @Nullable UUID issuedCouponId) {
        if (lineSnapshots.isEmpty()) {
            throw new InvalidOrderException(OrderErrorCode.EMPTY_ORDER);
        }
        UUID orderId = UuidV7Generator.generate();
        Order order = new Order(orderId, generateOrderNumber(orderId), memberId, shippingAddress, shippingFee);
        for (OrderLineSnapshot snapshot : lineSnapshots) {
            order.lines.add(OrderLine.create(order, snapshot));
        }
        order.totalAmount = order.computeTotal();
        order.applyDiscount(discountAmount, issuedCouponId);
        return order;
    }

    /** 결제를 완료한다. 이행을 준비 중으로 전진시킨다. */
    public void markPaid() {
        if (status != OrderStatus.PENDING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.PAID;
        this.fulfillmentStatus = FulfillmentStatus.PREPARING;
        this.paidAt = Instant.now();
    }

    /**
     * 주문을 취소한다.
     *
     * @throws OrderStatusException 이미 취소·환불됐거나 출고 이후면
     */
    public void cancel(CancellationReason reason) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status == OrderStatus.PAID && isShippedOrDelivered()) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason;
    }

    /**
     * 배송 완료된 주문을 전체 반품 환불 처리한다. 이행 축은 DELIVERED로 남는다.
     *
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    public void refund(RefundReason reason) {
        if (status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.REFUND_NOT_ALLOWED);
        }
        this.status = OrderStatus.REFUNDED;
        this.refundedAt = Instant.now();
        this.refundReason = reason;
    }

    /** 출고한다. */
    public void ship() {
        requirePaid();
        requireFulfillment(FulfillmentStatus.PREPARING);
        this.fulfillmentStatus = FulfillmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    /** 배송 완료 처리한다. */
    public void confirmDelivery() {
        requirePaid();
        requireFulfillment(FulfillmentStatus.SHIPPED);
        this.fulfillmentStatus = FulfillmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    /** 이행을 보류한다. */
    public void holdFulfillment(HoldReason reason) {
        requirePaid();
        requireFulfillment(FulfillmentStatus.PREPARING);
        this.fulfillmentStatus = FulfillmentStatus.ON_HOLD;
        this.holdReason = reason;
    }

    /** 이행 보류를 해제한다. */
    public void releaseFulfillment() {
        requirePaid();
        requireFulfillment(FulfillmentStatus.ON_HOLD);
        this.fulfillmentStatus = FulfillmentStatus.PREPARING;
        this.holdReason = null;
    }

    private Money computeTotal() {
        return lines.stream().map(OrderLine::lineAmount).reduce(Money.ZERO, Money::plus);
    }

    private void applyDiscount(Money discountAmount, @Nullable UUID issuedCouponId) {
        if (totalAmount.isLessThan(discountAmount)) {
            throw new InvalidOrderException(OrderErrorCode.DISCOUNT_EXCEEDS_TOTAL);
        }
        boolean hasCoupon = issuedCouponId != null;
        boolean hasDiscount = !discountAmount.isZero();
        if (hasCoupon != hasDiscount) {
            throw new InvalidOrderException(OrderErrorCode.INVALID_DISCOUNT_COUPON);
        }
        this.discountAmount = discountAmount;
        this.issuedCouponId = issuedCouponId;
        this.payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
    }

    private boolean isShippedOrDelivered() {
        return fulfillmentStatus == FulfillmentStatus.SHIPPED || fulfillmentStatus == FulfillmentStatus.DELIVERED;
    }

    private void requirePaid() {
        if (status != OrderStatus.PAID) {
            throw new FulfillmentStatusException(OrderErrorCode.NOT_PAID);
        }
    }

    private void requireFulfillment(FulfillmentStatus expected) {
        if (fulfillmentStatus != expected) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
    }

    private static String generateOrderNumber(UUID orderId) {
        // UUIDv7의 난수 꼬리(마지막 세그먼트)를 붙여 같은 밀리초 동시 생성에도 충돌하지 않게 한다.
        return "ORD-" + System.currentTimeMillis() + "-"
                + orderId.toString().substring(24).toUpperCase(Locale.ROOT);
    }

    /** 비울 장바구니 라인을 특정하기 위한 주문된 변형 집합이다. */
    public Set<UUID> getOrderedVariantIds() {
        return lines.stream().map(OrderLine::getVariantId).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public FulfillmentStatus getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getShippingFee() {
        return shippingFee;
    }

    public Money getPayAmount() {
        return payAmount;
    }

    public @Nullable UUID getIssuedCouponId() {
        return issuedCouponId;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public @Nullable Instant getPaidAt() {
        return paidAt;
    }

    public @Nullable Instant getShippedAt() {
        return shippedAt;
    }

    public @Nullable Instant getDeliveredAt() {
        return deliveredAt;
    }

    public @Nullable Instant getCancelledAt() {
        return cancelledAt;
    }

    public @Nullable CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public @Nullable HoldReason getHoldReason() {
        return holdReason;
    }

    public @Nullable Instant getRefundedAt() {
        return refundedAt;
    }

    public @Nullable RefundReason getRefundReason() {
        return refundReason;
    }

    public Set<OrderLine> getLines() {
        return Collections.unmodifiableSet(lines);
    }
}
