package com.commerce.order.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.shared.entity.Money;
import com.commerce.shared.entity.MoneyConverter;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** 주문 애그리거트 루트다. 라인·배송지를 주문 시점 스냅샷으로 보관한다. */
@Entity
@Table(schema = "ordering", name = "orders")
public class Order extends BaseTimeEntity<UUID> {

    /** 주문 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 외부 노출용 주문 번호. */
    @Column(name = "order_number")
    private String orderNumber;

    /** 주문한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 결제 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    /** 이행 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status")
    private FulfillmentStatus fulfillmentStatus;

    /** 라인 합계. 할인·배송비 반영 전. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount")
    private Money totalAmount;

    /** 쿠폰 할인액. 라인 합계를 넘지 못한다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount")
    private Money discountAmount;

    /** 배송비. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "shipping_fee")
    private Money shippingFee;

    /** 실청구액. 라인 합계 − 할인액 + 배송비. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "pay_amount")
    private Money payAmount;

    /** 적용한 쿠폰 발급분 식별자. 할인액이 0이면 없다. */
    @Column(name = "issued_coupon_id")
    @Nullable
    private UUID issuedCouponId;

    /** 배송지. 주문 시점 스냅샷. */
    @Embedded
    private Address shippingAddress;

    /** 출고 시각. */
    @Column(name = "shipped_at")
    @Nullable
    private Instant shippedAt;

    /** 택배사. */
    @Column(name = "carrier")
    @Nullable
    private String carrier;

    /** 운송장 번호. */
    @Column(name = "tracking_number")
    @Nullable
    private String trackingNumber;

    /** 배송 완료 시각. */
    @Column(name = "delivered_at")
    @Nullable
    private Instant deliveredAt;

    /** 전 라인 재고 차감 완료 시각. */
    @Column(name = "stock_deducted_at")
    @Nullable
    private Instant stockDeductedAt;

    /** 결제 완료 시각. */
    @Column(name = "paid_at")
    @Nullable
    private Instant paidAt;

    /** 취소 개시 요청 시각. */
    @Column(name = "cancel_requested_at")
    @Nullable
    private Instant cancelRequestedAt;

    /** 취소 시각. */
    @Column(name = "cancelled_at")
    @Nullable
    private Instant cancelledAt;

    /** 취소 사유. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    @Nullable
    private CancellationReason cancellationReason;

    /** 이행 보류 사유. 보류 중일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason")
    @Nullable
    private HoldReason holdReason;

    /** 환불 시각. */
    @Column(name = "refunded_at")
    @Nullable
    private Instant refundedAt;

    /** 환불 사유. */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_reason")
    @Nullable
    private RefundReason refundReason;

    /** 반품 요청 축 상태. 요청이 없으면 없다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_status")
    @Nullable
    private ReturnStatus returnStatus;

    /** 반품 요청 시각. */
    @Column(name = "return_requested_at")
    @Nullable
    private Instant returnRequestedAt;

    /** 반품 요청 사유. 승인되면 이 사유로 환불한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason")
    @Nullable
    private RefundReason returnReason;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    /** 주문 라인 집합. */
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
     * 주문을 생성한다. 라인 합계를 자기 계산하고 할인·실청구액 불변식을 자기 강제한다.
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

    /**
     * 결제를 완료한다. 이행을 준비 중으로 전진시킨다.
     *
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    public void markPaid(Instant now) {
        if (status != OrderStatus.PENDING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.PAID;
        this.fulfillmentStatus = FulfillmentStatus.PREPARING;
        this.paidAt = now;
    }

    /**
     * 전 라인 재고 차감 완료를 기록한다.
     *
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    public void markStockDeducted(Instant now) {
        if (status != OrderStatus.PENDING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.stockDeductedAt = now;
    }

    /**
     * 주문을 취소한다.
     *
     * @throws OrderStatusException 이미 취소·환불됐거나 출고 이후면
     */
    public void cancel(CancellationReason reason, Instant now) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status == OrderStatus.PAID && isShippedOrDelivered()) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    /**
     * 취소 개시를 기록한다. 마커가 있는 동안 출고가 거부된다. 이미 개시된 주문에는 아무것도 하지 않는다.
     *
     * @throws OrderStatusException 결제 완료 주문이 아니거나 출고 이후면
     */
    public void requestCancellation(Instant now) {
        if (cancelRequestedAt != null) {
            return;
        }
        if (status != OrderStatus.PAID) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (isShippedOrDelivered()) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.cancelRequestedAt = now;
    }

    /**
     * 배송 완료된 주문을 전체 반품 환불 처리한다. 이행 축은 DELIVERED로 남는다.
     *
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    public void refund(RefundReason reason, Instant now) {
        if (status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.REFUND_NOT_ALLOWED);
        }
        this.status = OrderStatus.REFUNDED;
        this.refundedAt = now;
        this.refundReason = reason;
        if (returnStatus == ReturnStatus.REQUESTED) {
            this.returnStatus = ReturnStatus.COMPLETED;
        }
    }

    /**
     * 반품을 요청한다. 거절된 요청은 새 요청으로 덮어쓴다.
     *
     * @throws OrderStatusException 이미 요청 중이거나, 배송 완료된 결제 주문이 아니면
     */
    public void requestReturn(RefundReason reason, Instant now) {
        if (returnStatus == ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_ALREADY_REQUESTED);
        }
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_ALLOWED);
        }
        this.returnStatus = ReturnStatus.REQUESTED;
        this.returnRequestedAt = now;
        this.returnReason = reason;
    }

    /**
     * 반품 요청을 거절한다. 주문은 PAID·DELIVERED로 남는다.
     *
     * @throws OrderStatusException 반품 요청 상태가 아니면
     */
    public void rejectReturn() {
        if (returnStatus != ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_REQUESTED);
        }
        this.returnStatus = ReturnStatus.REJECTED;
    }

    /**
     * 출고한다. 택배사·운송장 번호를 기록한다.
     *
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나, 준비 중이 아니거나, 취소가 진행 중이면
     */
    public void ship(String carrier, String trackingNumber, Instant now) {
        requirePaid();
        requireFulfillment(FulfillmentStatus.PREPARING);
        if (cancelRequestedAt != null) {
            throw new FulfillmentStatusException(OrderErrorCode.CANCEL_IN_PROGRESS);
        }
        this.fulfillmentStatus = FulfillmentStatus.SHIPPED;
        this.shippedAt = now;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /**
     * 배송 완료 처리한다.
     *
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 출고된 상태가 아니면
     */
    public void confirmDelivery(Instant now) {
        requirePaid();
        requireFulfillment(FulfillmentStatus.SHIPPED);
        this.fulfillmentStatus = FulfillmentStatus.DELIVERED;
        this.deliveredAt = now;
    }

    /**
     * 이행을 보류한다.
     *
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 준비 중이 아니면
     */
    public void holdFulfillment(HoldReason reason) {
        requirePaid();
        requireFulfillment(FulfillmentStatus.PREPARING);
        this.fulfillmentStatus = FulfillmentStatus.ON_HOLD;
        this.holdReason = reason;
    }

    /**
     * 이행 보류를 해제한다.
     *
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 보류 중이 아니면
     */
    public void releaseFulfillment() {
        requirePaid();
        requireFulfillment(FulfillmentStatus.ON_HOLD);
        this.fulfillmentStatus = FulfillmentStatus.PREPARING;
        this.holdReason = null;
    }

    /** 라인 금액을 합산해 라인 합계를 계산한다. */
    private Money computeTotal() {
        return lines.stream().map(OrderLine::lineAmount).reduce(Money.ZERO, Money::plus);
    }

    /** 할인·쿠폰 불변식을 검사하고 할인액·쿠폰·실청구액을 채운다. */
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

    /** 출고 이후 상태인지 판정한다. */
    private boolean isShippedOrDelivered() {
        return fulfillmentStatus == FulfillmentStatus.SHIPPED || fulfillmentStatus == FulfillmentStatus.DELIVERED;
    }

    /** 결제 완료 상태가 아니면 거부한다. */
    private void requirePaid() {
        if (status != OrderStatus.PAID) {
            throw new FulfillmentStatusException(OrderErrorCode.NOT_PAID);
        }
    }

    /** 이행 상태가 기대값이 아니면 거부한다. */
    private void requireFulfillment(FulfillmentStatus expected) {
        if (fulfillmentStatus != expected) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
    }

    /** 주문 식별자에서 외부 노출용 주문 번호를 만든다. */
    private static String generateOrderNumber(UUID orderId) {
        // UUIDv7의 난수 꼬리(마지막 세그먼트)를 붙여 같은 밀리초 동시 생성에도 충돌하지 않게 한다.
        return "ORD-" + System.currentTimeMillis() + "-"
                + orderId.toString().substring(24).toUpperCase(Locale.ROOT);
    }

    /** 주문된 변형 집합을 변경 불가 뷰로 반환한다. */
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

    public @Nullable Instant getStockDeductedAt() {
        return stockDeductedAt;
    }

    public @Nullable Instant getPaidAt() {
        return paidAt;
    }

    public @Nullable Instant getShippedAt() {
        return shippedAt;
    }

    public @Nullable String getCarrier() {
        return carrier;
    }

    public @Nullable String getTrackingNumber() {
        return trackingNumber;
    }

    public @Nullable Instant getDeliveredAt() {
        return deliveredAt;
    }

    public @Nullable Instant getCancelRequestedAt() {
        return cancelRequestedAt;
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

    public @Nullable ReturnStatus getReturnStatus() {
        return returnStatus;
    }

    public @Nullable Instant getReturnRequestedAt() {
        return returnRequestedAt;
    }

    public @Nullable RefundReason getReturnReason() {
        return returnReason;
    }

    /** 주문 라인 집합을 변경 불가 뷰로 반환한다. */
    public Set<OrderLine> getLines() {
        return Collections.unmodifiableSet(lines);
    }

    public long getVersion() {
        return version;
    }
}
