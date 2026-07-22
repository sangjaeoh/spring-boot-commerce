package com.commerce.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidityPeriodTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    @DisplayName("시작이 종료보다 늦거나 같으면 거부한다")
    void rejectsNonIncreasingRange() {
        assertThatThrownBy(() -> ValidityPeriod.of(UNTIL, FROM)).isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> ValidityPeriod.of(FROM, FROM)).isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("기간 내 시각은 유효하다")
    void validWithinPeriod() {
        assertThat(ValidityPeriod.of(FROM, UNTIL).isValidAt(FROM.plus(1, ChronoUnit.DAYS)))
                .isTrue();
    }

    @Test
    @DisplayName("기간 밖 시각은 유효하지 않다")
    void invalidOutsidePeriod() {
        ValidityPeriod period = ValidityPeriod.of(FROM, UNTIL);
        assertThat(period.isValidAt(FROM.minus(1, ChronoUnit.DAYS))).isFalse();
        assertThat(period.isValidAt(UNTIL.plus(1, ChronoUnit.DAYS))).isFalse();
    }
}
