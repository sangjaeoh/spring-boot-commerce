package com.commerce.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.coupon.domain.exception.CouponStatusException;
import com.commerce.coupon.domain.exception.InvalidCouponException;
import com.commerce.shared.entity.Money;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponTest {

    private static final Instant FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant IN_PERIOD = Instant.parse("2026-01-01T00:00:00Z");

    private Coupon activeCoupon() {
        return Coupon.create("정률 10%", Discount.rate(10), Money.of(10000L), ValidityPeriod.of(FROM, UNTIL), 30, null);
    }

    @Test
    @DisplayName("생성 시 ACTIVE다")
    void createsActive() {
        assertThat(activeCoupon().getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("사용 창이 1 미만이면 생성할 수 없다")
    void rejectsUsageValidDaysBelowOne() {
        assertThatThrownBy(() ->
                        Coupon.create("x", Discount.rate(10), Money.ZERO, ValidityPeriod.of(FROM, UNTIL), 0, null))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("발급 한도가 1 미만이면 생성할 수 없다")
    void rejectsMaxIssuanceBelowOne() {
        assertThatThrownBy(
                        () -> Coupon.create("x", Discount.rate(10), Money.ZERO, ValidityPeriod.of(FROM, UNTIL), 30, 0))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("발급 한도가 없으면 무제한, 있으면 한도 걸림으로 판정된다")
    void hasIssuanceLimitReflectsMaxIssuance() {
        assertThat(activeCoupon().hasIssuanceLimit()).isFalse();
        assertThat(Coupon.create("한도", Discount.rate(10), Money.ZERO, ValidityPeriod.of(FROM, UNTIL), 30, 100)
                        .hasIssuanceLimit())
                .isTrue();
    }

    @Test
    @DisplayName("발급 중지와 재개를 오간다")
    void disableAndEnable() {
        Coupon coupon = activeCoupon();
        coupon.disable();
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DISABLED);
        coupon.enable();
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 중지된 쿠폰은 다시 중지할 수 없다")
    void cannotDisableTwice() {
        Coupon coupon = activeCoupon();
        coupon.disable();
        assertThatThrownBy(coupon::disable).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("ACTIVE이고 발급 기간 내면 발급 가능하다")
    void issuableWhenActiveInPeriod() {
        assertThatCode(() -> activeCoupon().checkIssuable(IN_PERIOD)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("중지 상태이거나 발급 기간 밖이면 발급 불가")
    void notIssuableWhenDisabledOrOutsidePeriod() {
        Coupon disabled = activeCoupon();
        disabled.disable();
        assertThatThrownBy(() -> disabled.checkIssuable(IN_PERIOD)).isInstanceOf(CouponStatusException.class);
        assertThatThrownBy(() -> activeCoupon().checkIssuable(UNTIL.plusSeconds(1)))
                .isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("발급 기간 경계 정각에는 발급 가능하고 직전·직후에는 불가하다")
    void issuablePeriodBoundaries() {
        assertThatCode(() -> activeCoupon().checkIssuable(FROM)).doesNotThrowAnyException();
        assertThatCode(() -> activeCoupon().checkIssuable(UNTIL)).doesNotThrowAnyException();
        assertThatThrownBy(() -> activeCoupon().checkIssuable(FROM.minusMillis(1)))
                .isInstanceOf(CouponStatusException.class);
        assertThatThrownBy(() -> activeCoupon().checkIssuable(UNTIL.plusMillis(1)))
                .isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("주문금액이 최소주문금액 이상이면 할인 정책에 위임하고, 미달이면 0")
    void calculateDiscountFloorsAtMinOrderAmount() {
        assertThat(activeCoupon().calculateDiscount(Money.of(50000L))).isEqualTo(Money.of(5000L));
        assertThat(activeCoupon().calculateDiscount(Money.of(5000L))).isEqualTo(Money.ZERO);
    }
}
