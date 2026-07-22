package com.commerce.coupon.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.shared.entity.Money;
import com.commerce.shared.entity.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 애그리거트 루트다. */
@Entity
@Table(schema = "coupon", name = "coupon")
public class Coupon extends BaseTimeEntity<UUID> {

    /** 쿠폰 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 쿠폰명. */
    @Column(name = "name")
    private String name;

    /** 할인 정책. */
    @Embedded
    private Discount discount;

    /** 최소 주문 금액. 할인 전 주문 금액 기준이고 0일 수 있다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "min_order_amount")
    private Money minOrderAmount;

    /** 발급 가능 기간. */
    @Embedded
    private ValidityPeriod validity;

    /** 발급분 사용 창(일). 발급 시각에 더해 발급분 사용 기한이 된다. 1 이상. */
    @Column(name = "usage_valid_days")
    private int usageValidDays;

    /** 총 발급 한도. 없으면 무제한이고, 있으면 1 이상. */
    @Column(name = "max_issuance")
    @Nullable
    private Integer maxIssuance;

    /** 발급 소진 카운트. 발급 한도와 비교하는 누적 발급 수. */
    @Column(name = "issued_count")
    private int issuedCount;

    /** 발급 가능 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CouponStatus status;

    protected Coupon() {}

    private Coupon(
            UUID id,
            String name,
            Discount discount,
            Money minOrderAmount,
            ValidityPeriod validity,
            int usageValidDays,
            @Nullable Integer maxIssuance) {
        this.id = id;
        this.name = name;
        this.discount = discount;
        this.minOrderAmount = minOrderAmount;
        this.validity = validity;
        this.usageValidDays = usageValidDays;
        this.maxIssuance = maxIssuance;
        this.issuedCount = 0;
        this.status = CouponStatus.ACTIVE;
    }

    /**
     * 발급 가능({@code ACTIVE}) 상태로 쿠폰 정책을 생성한다. 발급 한도는 선택이며 없으면 무제한이다.
     *
     * @throws InvalidCouponException 사용 유효일수가 1 미만이거나 발급 한도가 1 미만이면
     */
    public static Coupon create(
            String name,
            Discount discount,
            Money minOrderAmount,
            ValidityPeriod validity,
            int usageValidDays,
            @Nullable Integer maxIssuance) {
        if (usageValidDays < 1) {
            throw new InvalidCouponException(CouponErrorCode.INVALID_USAGE_VALID_DAYS);
        }
        if (maxIssuance != null && maxIssuance < 1) {
            throw new InvalidCouponException(CouponErrorCode.INVALID_MAX_ISSUANCE);
        }
        return new Coupon(
                UuidV7Generator.generate(), name, discount, minOrderAmount, validity, usageValidDays, maxIssuance);
    }

    /**
     * 발급을 중지한다.
     *
     * @throws CouponStatusException 발급 가능 상태가 아니면
     */
    public void disable() {
        if (status != CouponStatus.ACTIVE) {
            throw new CouponStatusException(CouponErrorCode.INVALID_COUPON_STATE_TRANSITION);
        }
        this.status = CouponStatus.DISABLED;
    }

    /**
     * 발급을 재개한다.
     *
     * @throws CouponStatusException 발급 중지 상태가 아니면
     */
    public void enable() {
        if (status != CouponStatus.DISABLED) {
            throw new CouponStatusException(CouponErrorCode.INVALID_COUPON_STATE_TRANSITION);
        }
        this.status = CouponStatus.ACTIVE;
    }

    /**
     * 발급 가능한지 검사한다.
     *
     * @throws CouponStatusException 중지 상태이거나 발급 가능 기간 밖이면
     */
    public void checkIssuable(Instant now) {
        if (status != CouponStatus.ACTIVE) {
            throw new CouponStatusException(CouponErrorCode.COUPON_DISABLED);
        }
        if (!validity.isValidAt(now)) {
            throw new CouponStatusException(CouponErrorCode.COUPON_OUTSIDE_ISSUE_PERIOD);
        }
    }

    /** 발급 한도가 설정돼 있는지 본다. */
    public boolean hasIssuanceLimit() {
        return maxIssuance != null;
    }

    /** 주문 금액이 최소 주문 금액을 충족하는지 본다. */
    public boolean isMinOrderAmountMet(Money orderAmount) {
        return orderAmount.isGreaterThanOrEqualTo(minOrderAmount);
    }

    /** 주문 금액에 대한 할인액을 산출한다. 최소주문금액 미달이면 0을 반환한다. */
    public Money calculateDiscount(Money orderAmount) {
        if (!isMinOrderAmountMet(orderAmount)) {
            return Money.ZERO;
        }
        return discount.applyTo(orderAmount);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return name;
    }

    public Discount getDiscount() {
        return discount;
    }

    public Money getMinOrderAmount() {
        return minOrderAmount;
    }

    public ValidityPeriod getValidity() {
        return validity;
    }

    public int getUsageValidDays() {
        return usageValidDays;
    }

    public @Nullable Integer getMaxIssuance() {
        return maxIssuance;
    }

    public int getIssuedCount() {
        return issuedCount;
    }

    public CouponStatus getStatus() {
        return status;
    }
}
