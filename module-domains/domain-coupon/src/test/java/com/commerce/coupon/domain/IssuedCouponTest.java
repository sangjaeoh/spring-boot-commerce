package com.commerce.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.coupon.domain.exception.CouponExpiredException;
import com.commerce.coupon.domain.exception.CouponStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IssuedCouponTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(30, ChronoUnit.DAYS);

    private IssuedCoupon issued() {
        return IssuedCoupon.create(UUID.randomUUID(), UUID.randomUUID(), EXPIRES_AT);
    }

    @Test
    @DisplayName("생성 시 ISSUED이고 사용 정보가 비어 있다")
    void createsIssued() {
        IssuedCoupon coupon = issued();
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("사용하면 USED이고 사용 정보가 기록된다")
    void useSetsUsedInfo() {
        IssuedCoupon coupon = issued();
        UUID orderId = UUID.randomUUID();
        coupon.use(orderId, NOW);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.getUsedAt()).isEqualTo(NOW);
        assertThat(coupon.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("이미 사용된 발급분은 다시 사용할 수 없다")
    void cannotUseWhenAlreadyUsed() {
        IssuedCoupon coupon = issued();
        coupon.use(UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> coupon.use(UUID.randomUUID(), NOW)).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("사용 기한 정각까지는 사용할 수 있다")
    void canUseAtExactExpiry() {
        IssuedCoupon coupon = issued();
        coupon.use(UUID.randomUUID(), EXPIRES_AT);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
    }

    @Test
    @DisplayName("사용 기한 직후에는 사용할 수 없다")
    void cannotUseJustAfterExpiry() {
        IssuedCoupon coupon = issued();
        assertThatThrownBy(() -> coupon.use(UUID.randomUUID(), EXPIRES_AT.plusMillis(1)))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("무효화하면 REVOKED이고 시각·사유가 기록된다")
    void revokeSetsRevocationInfo() {
        IssuedCoupon coupon = issued();
        coupon.revoke("오발급 회수", NOW);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.REVOKED);
        assertThat(coupon.getRevokedAt()).isNotNull();
        assertThat(coupon.getRevokeReason()).isEqualTo("오발급 회수");
    }

    @Test
    @DisplayName("사용된 발급분은 무효화할 수 없다")
    void cannotRevokeWhenUsed() {
        IssuedCoupon coupon = issued();
        coupon.use(UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> coupon.revoke("오발급 회수", NOW)).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("이미 무효화된 발급분은 다시 무효화할 수 없다")
    void cannotRevokeTwice() {
        IssuedCoupon coupon = issued();
        coupon.revoke("오발급 회수", NOW);
        assertThatThrownBy(() -> coupon.revoke("오발급 회수", NOW)).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("무효화된 발급분은 사용할 수 없다")
    void cannotUseWhenRevoked() {
        IssuedCoupon coupon = issued();
        coupon.revoke("오발급 회수", NOW);
        assertThatThrownBy(() -> coupon.use(UUID.randomUUID(), NOW)).isInstanceOf(CouponStatusException.class);
    }

    @Test
    @DisplayName("사용 주문으로 복원하면 ISSUED로 돌아가고 사용 정보가 지워진다")
    void restoreUseRevertsToIssued() {
        IssuedCoupon coupon = issued();
        UUID orderId = UUID.randomUUID();
        coupon.use(orderId, NOW);
        coupon.restoreUse(orderId);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("사용 상태가 아니면 복원은 아무 일도 하지 않는다")
    void restoreUseNoOpWhenIssued() {
        IssuedCoupon coupon = issued();
        coupon.restoreUse(UUID.randomUUID());
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("다른 주문에 사용된 발급분의 복원은 아무 일도 하지 않는다")
    void restoreUseNoOpWhenUsedByAnotherOrder() {
        IssuedCoupon coupon = issued();
        UUID orderId = UUID.randomUUID();
        coupon.use(orderId, NOW);
        coupon.restoreUse(UUID.randomUUID());
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.getUsedAt()).isEqualTo(NOW);
        assertThat(coupon.getOrderId()).isEqualTo(orderId);
    }
}
