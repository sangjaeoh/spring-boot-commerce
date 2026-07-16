package com.commerce.coupon.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.exception.InvalidCouponException;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.google.errorprone.annotations.Keep;
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

/**
 * 쿠폰 정책 애그리거트 루트다. 최초 상태는 {@code ACTIVE}다.
 *
 * <p>발급 후 정책이 {@code DISABLED}가 돼도 이미 발급된 쿠폰은 계속 사용할 수 있다(신규 발급만 막는다).
 * 발급 한도({@code maxIssuance})는 선택이며, 소진 카운트({@code issuedCount})는 경합 안전을 위해
 * 리포지토리의 원자적 조건부 UPDATE로만 증가한다.
 */
@Entity
@Table(schema = "coupon", name = "coupon")
public class Coupon extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "name")
    private String name;

    @Embedded
    private Discount discount;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "min_order_amount")
    private Money minOrderAmount;

    @Embedded
    private ValidityPeriod validity;

    @Column(name = "usage_valid_days")
    private int usageValidDays;

    @Column(name = "max_issuance")
    @Nullable
    private Integer maxIssuance;

    @Keep
    @Column(name = "issued_count")
    private int issuedCount;

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

    /** 발급 가능({@code ACTIVE}) 상태로 쿠폰 정책을 생성한다. 발급 한도는 선택이며 없으면 무제한이다. */
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

    /** 발급을 중지한다. */
    public void disable() {
        if (status != CouponStatus.ACTIVE) {
            throw new CouponStatusException(CouponErrorCode.INVALID_COUPON_STATE_TRANSITION);
        }
        this.status = CouponStatus.DISABLED;
    }

    /** 발급을 재개한다. */
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

    /** 발급 한도가 걸려 있는지 판정한다. */
    public boolean hasIssuanceLimit() {
        return maxIssuance != null;
    }

    /** 주문 금액에 대한 할인액을 산출한다. 최소주문금액 미달이면 0을 반환한다. */
    public Money calculateDiscount(Money orderAmount) {
        if (orderAmount.isLessThan(minOrderAmount)) {
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

    public Money getMinOrderAmount() {
        return minOrderAmount;
    }

    public int getUsageValidDays() {
        return usageValidDays;
    }

    public CouponStatus getStatus() {
        return status;
    }
}
