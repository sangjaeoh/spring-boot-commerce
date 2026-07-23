package com.commerce.domain.shared.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("음수 금액은 생성할 수 없다")
    void negativeAmountIsRejected() {
        assertThatThrownBy(() -> Money.of(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("더하기는 새 금액을 만든다")
    void plusProducesNewMoney() {
        assertThat(Money.of(1000L).plus(Money.of(500L))).isEqualTo(Money.of(1500L));
    }

    @Test
    @DisplayName("빼기는 새 금액을 만든다")
    void minusProducesNewMoney() {
        assertThat(Money.of(1000L).minus(Money.of(400L))).isEqualTo(Money.of(600L));
    }

    @Test
    @DisplayName("빼기 결과가 음수면 예외")
    void minusThrowsWhenResultNegative() {
        assertThatThrownBy(() -> Money.of(500L).minus(Money.of(1000L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("곱하기는 수량만큼 배수한다")
    void multiplyScalesByFactor() {
        assertThat(Money.of(1000L).multiply(3L)).isEqualTo(Money.of(3000L));
    }

    @Test
    @DisplayName("음수 곱수는 예외")
    void multiplyRejectsNegativeFactor() {
        assertThatThrownBy(() -> Money.of(1000L).multiply(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("크기 비교")
    void comparisons() {
        assertThat(Money.of(1000L).isGreaterThanOrEqualTo(Money.of(1000L))).isTrue();
        assertThat(Money.of(999L).isLessThan(Money.of(1000L))).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
    }
}
