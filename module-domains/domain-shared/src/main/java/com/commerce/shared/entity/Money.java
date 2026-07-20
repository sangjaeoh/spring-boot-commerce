package com.commerce.shared.entity;

/**
 * 원 단위 정수 금액을 나타내는 값 객체다.
 *
 * <p>금액은 항상 0 이상이며, 통화는 KRW 단일로 가정한다.
 */
public record Money(long amount) {

    public static final Money ZERO = new Money(0L);

    public Money {
        if (amount < 0L) {
            throw new IllegalArgumentException("금액은 0 이상이어야 한다: " + amount);
        }
    }

    /** 원 단위 금액으로 값을 만든다. */
    public static Money of(long amount) {
        return new Money(amount);
    }

    /** 두 금액을 더한 값을 만든다. */
    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    /** 이 금액에서 다른 금액을 뺀 값을 만든다. */
    public Money minus(Money other) {
        if (this.amount < other.amount) {
            throw new IllegalArgumentException("뺄셈 결과가 음수가 될 수 없다: " + this.amount + " - " + other.amount);
        }
        return new Money(this.amount - other.amount);
    }

    /** 이 금액을 정수배한 값을 만든다. */
    public Money multiply(long times) {
        if (times < 0L) {
            throw new IllegalArgumentException("곱수는 0 이상이어야 한다: " + times);
        }
        return new Money(this.amount * times);
    }

    /** 금액이 0인지 본다. */
    public boolean isZero() {
        return this.amount == 0L;
    }

    /** 이 금액이 다른 금액 이상인지 본다. */
    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.amount >= other.amount;
    }

    /** 이 금액이 다른 금액 미만인지 본다. */
    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }
}
