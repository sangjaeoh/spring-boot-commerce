package com.commerce.domain.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.coupon.domain.exception.InvalidCouponException;
import com.commerce.domain.shared.entity.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiscountTest {

    @Test
    @DisplayName("정액 할인은 주문 금액과 할인액 중 작은 값이다")
    void fixedIsMinOfAmountAndOrder() {
        assertThat(Discount.fixed(Money.of(1000L)).applyTo(Money.of(5000L))).isEqualTo(Money.of(1000L));
        assertThat(Discount.fixed(Money.of(1000L)).applyTo(Money.of(500L))).isEqualTo(Money.of(500L));
    }

    @Test
    @DisplayName("정률 할인은 내림한 퍼센트 금액이다")
    void rateFloorsPercent() {
        assertThat(Discount.rate(10).applyTo(Money.of(5000L))).isEqualTo(Money.of(500L));
        assertThat(Discount.rate(10).applyTo(Money.of(999L))).isEqualTo(Money.of(99L));
    }

    @Test
    @DisplayName("정률 상한이 있으면 상한으로 제한한다")
    void rateAppliesMaxCap() {
        assertThat(Discount.rate(50, Money.of(1000L)).applyTo(Money.of(5000L))).isEqualTo(Money.of(1000L));
    }

    @Test
    @DisplayName("할인액은 항상 주문 금액 이하다")
    void neverExceedsOrderAmount() {
        assertThat(Discount.rate(100).applyTo(Money.of(5000L))).isEqualTo(Money.of(5000L));
    }

    @Test
    @DisplayName("정액 할인액이 1 미만이면 거부한다")
    void rejectsFixedBelowOne() {
        assertThatThrownBy(() -> Discount.fixed(Money.ZERO)).isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("정률 퍼센트가 범위 밖이면 거부한다")
    void rejectsPercentOutOfRange() {
        assertThatThrownBy(() -> Discount.rate(0)).isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Discount.rate(101)).isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("형과 필드가 불일치하는 조합은 거부한다")
    void rejectsInconsistentTypeAndFields() {
        assertThatThrownBy(() -> new Discount(DiscountType.FIXED, Money.of(1000L), 10, null))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.RATE, Money.of(1000L), 10, null))
                .isInstanceOf(InvalidCouponException.class);
    }
}
