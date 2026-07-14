package com.commerce.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.coupon.exception.CouponExpiredException;
import com.commerce.coupon.exception.CouponStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IssuedCouponTest {

    private IssuedCoupon issued(Instant expiresAt) {
        return IssuedCoupon.create(UUID.randomUUID(), UUID.randomUUID(), expiresAt);
    }

    private Instant future() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("생성 시 ISSUED이고 사용 정보가 비어 있다")
    void createsIssued() {
        IssuedCoupon coupon = issued(future());
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("사용하면 USED이고 사용 정보가 기록된다")
    void useSetsUsedInfo() {
        IssuedCoupon coupon = issued(future());
        UUID orderId = UUID.randomUUID();
        coupon.use(orderId);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.getUsedAt()).isNotNull();
        assertThat(coupon.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("이미 사용된 발급분은 다시 사용할 수 없다")
    void cannotUseWhenAlreadyUsed() {
        IssuedCoupon coupon = issued(future());
        coupon.use(UUID.randomUUID());
        assertThatThrownBy(() -> coupon.use(UUID.randomUUID())).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("사용 기한이 지난 발급분은 사용할 수 없다")
    void cannotUseWhenExpired() {
        IssuedCoupon coupon = issued(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThatThrownBy(() -> coupon.use(UUID.randomUUID())).isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("복원하면 ISSUED로 돌아가고 사용 정보가 지워진다")
    void restoreUseRevertsToIssued() {
        IssuedCoupon coupon = issued(future());
        coupon.use(UUID.randomUUID());
        coupon.restoreUse();
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("사용 상태가 아니면 복원은 아무 일도 하지 않는다")
    void restoreUseNoOpWhenIssued() {
        IssuedCoupon coupon = issued(future());
        coupon.restoreUse();
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
    }
}
