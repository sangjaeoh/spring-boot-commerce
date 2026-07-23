package com.commerce.domain.order.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 이행(fulfillment) 애그리거트 루트다. 결제 완료된 주문마다 하나씩, 결제 완료 시점에 준비 중으로 생성된다. */
@Entity
@Table(schema = "ordering", name = "fulfillment")
public class Fulfillment extends BaseTimeEntity<UUID> {

    /** 이행 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 이행 대상 주문 식별자. order 애그리거트 논리 참조. */
    @Column(name = "order_id")
    private UUID orderId;

    /** 이행 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FulfillmentStatus status;

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

    /** 이행 보류 사유. 보류 중일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason")
    @Nullable
    private HoldReason holdReason;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    protected Fulfillment() {}

    private Fulfillment(UUID id, UUID orderId) {
        this.id = id;
        this.orderId = orderId;
        this.status = FulfillmentStatus.PREPARING;
    }

    /** 결제 완료된 주문의 이행을 준비 중으로 생성한다. */
    public static Fulfillment create(UUID orderId) {
        return new Fulfillment(UuidV7Generator.generate(), orderId);
    }

    /**
     * 출고한다. 택배사·운송장 번호를 기록한다.
     *
     * @throws FulfillmentStatusException 준비 중이 아니거나 주문의 취소가 진행 중이면
     */
    public void ship(String carrier, String trackingNumber, boolean orderCancelInProgress, Instant now) {
        if (status != FulfillmentStatus.PREPARING) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        if (orderCancelInProgress) {
            throw new FulfillmentStatusException(OrderErrorCode.CANCEL_IN_PROGRESS);
        }
        this.status = FulfillmentStatus.SHIPPED;
        this.shippedAt = now;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /**
     * 배송 완료 처리한다.
     *
     * @throws FulfillmentStatusException 출고된 상태가 아니면
     */
    public void confirmDelivery(Instant now) {
        if (status != FulfillmentStatus.SHIPPED) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.DELIVERED;
        this.deliveredAt = now;
    }

    /**
     * 이행을 보류한다.
     *
     * @throws FulfillmentStatusException 준비 중이 아니면
     */
    public void hold(HoldReason reason) {
        if (status != FulfillmentStatus.PREPARING) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.ON_HOLD;
        this.holdReason = reason;
    }

    /**
     * 이행 보류를 해제한다.
     *
     * @throws FulfillmentStatusException 보류 중이 아니면
     */
    public void release() {
        if (status != FulfillmentStatus.ON_HOLD) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.PREPARING;
        this.holdReason = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public FulfillmentStatus getStatus() {
        return status;
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

    public @Nullable HoldReason getHoldReason() {
        return holdReason;
    }

    public long getVersion() {
        return version;
    }
}
