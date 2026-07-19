package com.commerce.core.money;

/**
 * 원 단위 정수 금액을 나타내는 값 객체다.
 *
 * <p>금액은 항상 0 이상이며, 통화는 KRW 단일로 가정한다. 불변이라 산술은 새 인스턴스를 만들고,
 * {@code minus}가 음수를 낼 조합은 예외로 배제한다.
 */
public record Money(long amount) {

    public static final Money ZERO = new Money(0L);

    public Money {
        if (amount < 0L) {
            throw new IllegalArgumentException("금액은 0 이상이어야 한다: " + amount);
        }
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(Money other) {
        if (this.amount < other.amount) {
            throw new IllegalArgumentException("뺄셈 결과가 음수가 될 수 없다: " + this.amount + " - " + other.amount);
        }
        return new Money(this.amount - other.amount);
    }

    public Money multiply(long times) {
        if (times < 0L) {
            throw new IllegalArgumentException("곱수는 0 이상이어야 한다: " + times);
        }
        return new Money(this.amount * times);
    }

    public boolean isZero() {
        return this.amount == 0L;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }
}
