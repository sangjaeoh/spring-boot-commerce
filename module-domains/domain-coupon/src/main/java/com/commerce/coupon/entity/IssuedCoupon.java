package com.commerce.coupon.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponExpiredException;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원에게 발급된 쿠폰 애그리거트 루트다. 동시 사용을 낙관락({@code @Version})으로 막는다.
 *
 * <p>사용 기한은 발급 시 확정한 스냅샷({@code expiresAt})이며 정책 변경이 소급하지 않는다.
 * 불변식 {@code status == USED ⇔ usedAt != null ∧ orderId != null},
 * {@code status == REVOKED ⇔ revokedAt != null ∧ revokeReason != null}.
 */
@Entity
@Table(schema = "coupon", name = "issued_coupon")
public class IssuedCoupon extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "member_id")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IssuedCouponStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    @Nullable
    private Instant usedAt;

    @Column(name = "order_id")
    @Nullable
    private UUID orderId;

    @Column(name = "revoked_at")
    @Nullable
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    @Nullable
    private String revokeReason;

    @Version
    @Column(name = "version")
    private long version;

    protected IssuedCoupon() {}

    private IssuedCoupon(UUID id, UUID couponId, UUID memberId, Instant expiresAt) {
        this.id = id;
        this.couponId = couponId;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
        this.status = IssuedCouponStatus.ISSUED;
    }

    /** 사용 기한을 확정해 발급분을 만든다. 최초 상태는 {@code ISSUED}다. */
    public static IssuedCoupon create(UUID couponId, UUID memberId, Instant expiresAt) {
        return new IssuedCoupon(UuidV7Generator.generate(), couponId, memberId, expiresAt);
    }

    /**
     * 발급분을 {@code now} 기준으로 사용 처리한다.
     *
     * @throws CouponStatusException 이미 사용됐으면
     * @throws CouponExpiredException 사용 기한이 지났으면
     */
    public void use(UUID orderId, Instant now) {
        if (status != IssuedCouponStatus.ISSUED) {
            throw new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_USABLE);
        }
        if (now.isAfter(expiresAt)) {
            throw new CouponExpiredException(CouponErrorCode.COUPON_EXPIRED);
        }
        this.status = IssuedCouponStatus.USED;
        this.usedAt = now;
        this.orderId = orderId;
    }

    /**
     * 발급분을 무효화한다. 무효화된 발급분은 사용이 거부된다.
     *
     * @throws CouponStatusException 미사용({@code ISSUED}) 상태가 아니면
     */
    public void revoke(String reason, Instant now) {
        if (status != IssuedCouponStatus.ISSUED) {
            throw new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_REVOCABLE);
        }
        this.status = IssuedCouponStatus.REVOKED;
        this.revokedAt = now;
        this.revokeReason = reason;
    }

    /** 해당 주문에 대한 사용을 복원한다. 사용 상태가 아니거나 사용 주문이 다르면 아무 일도 하지 않는다. */
    public void restoreUse(UUID orderId) {
        if (status != IssuedCouponStatus.USED || !orderId.equals(this.orderId)) {
            return;
        }
        this.status = IssuedCouponStatus.ISSUED;
        this.usedAt = null;
        this.orderId = null;
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public IssuedCouponStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getUsedAt() {
        return usedAt;
    }

    public @Nullable UUID getOrderId() {
        return orderId;
    }

    public @Nullable Instant getRevokedAt() {
        return revokedAt;
    }

    public @Nullable String getRevokeReason() {
        return revokeReason;
    }
}
