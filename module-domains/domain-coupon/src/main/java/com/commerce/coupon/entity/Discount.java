package com.commerce.coupon.entity;

import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.InvalidCouponException;
import com.commerce.shared.entity.Money;
import com.commerce.shared.entity.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 정액({@code FIXED})·정률({@code RATE}) 두 판별 형을 갖는 할인 정책 값 객체다.
 *
 * @param type 판별 형
 * @param amount 정액 할인액({@code FIXED}에서만 존재, 1 이상)
 * @param percent 정률 퍼센트({@code RATE}에서만 존재, 1..100)
 * @param maxCap 정률 상한({@code RATE}에서 선택, 있으면 0 초과)
 */
@Embeddable
public record Discount(
        @Enumerated(EnumType.STRING) @Column(name = "discount_type")
        DiscountType type,

        @Convert(converter = MoneyConverter.class) @Column(name = "discount_amount") @Nullable
        Money amount,

        @Column(name = "discount_percent") @Nullable Integer percent,

        @Convert(converter = MoneyConverter.class) @Column(name = "discount_max_cap") @Nullable
        Money maxCap) {

    private static final int MIN_PERCENT = 1;
    private static final int MAX_PERCENT = 100;

    public Discount {
        switch (type) {
            case FIXED -> {
                if (amount == null || amount.amount() < 1 || percent != null || maxCap != null) {
                    throw new InvalidCouponException(CouponErrorCode.INVALID_DISCOUNT);
                }
            }
            case RATE -> {
                if (amount != null
                        || percent == null
                        || percent < MIN_PERCENT
                        || percent > MAX_PERCENT
                        || (maxCap != null && maxCap.amount() <= 0L)) {
                    throw new InvalidCouponException(CouponErrorCode.INVALID_DISCOUNT);
                }
            }
        }
    }

    /**
     * 정액 할인 정책을 만든다.
     *
     * @throws InvalidCouponException 정액 할인액이 1원 미만이면
     */
    public static Discount fixed(Money amount) {
        return new Discount(DiscountType.FIXED, amount, null, null);
    }

    /**
     * 정률 할인 정책을 만든다.
     *
     * @throws InvalidCouponException 정률 퍼센트가 1..100 밖이면
     */
    public static Discount rate(int percent) {
        return new Discount(DiscountType.RATE, null, percent, null);
    }

    /**
     * 상한을 둔 정률 할인 정책을 만든다.
     *
     * @throws InvalidCouponException 정률 퍼센트가 1..100 밖이거나 상한이 0원 이하이면
     */
    public static Discount rate(int percent, Money maxCap) {
        return new Discount(DiscountType.RATE, null, percent, maxCap);
    }

    /** 주문 금액에 할인을 적용해 할인액을 반환한다. 결과는 항상 주문 금액 이하다. */
    public Money applyTo(Money orderAmount) {
        Money discount =
                switch (type) {
                    case FIXED -> Objects.requireNonNull(amount);
                    case RATE -> rateDiscount(orderAmount);
                };
        return min(discount, orderAmount);
    }

    /** 정률 할인액을 산출하고 상한이 있으면 상한으로 자른다. */
    private Money rateDiscount(Money orderAmount) {
        Money rated = Money.of(orderAmount.amount() * Objects.requireNonNull(percent) / MAX_PERCENT);
        return maxCap == null ? rated : min(rated, maxCap);
    }

    /** 두 금액 중 작은 쪽을 고른다. */
    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}
