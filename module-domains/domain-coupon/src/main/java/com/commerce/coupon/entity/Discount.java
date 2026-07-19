package com.commerce.coupon.entity;

import com.commerce.core.money.Money;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.InvalidCouponException;
import com.commerce.jpa.converter.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 할인 정책 값 객체다. 판별 형 정액({@code FIXED})·정률({@code RATE})을 단일 임베더블로 평탄화한다.
 *
 * <p>불법 조합은 생성 시 배제한다.
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

    public static Discount fixed(Money amount) {
        return new Discount(DiscountType.FIXED, amount, null, null);
    }

    public static Discount rate(int percent) {
        return new Discount(DiscountType.RATE, null, percent, null);
    }

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

    private Money rateDiscount(Money orderAmount) {
        Money rated = Money.of(orderAmount.amount() * Objects.requireNonNull(percent) / MAX_PERCENT);
        return maxCap == null ? rated : min(rated, maxCap);
    }

    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}
