package com.commerce.coupon.domain;

import com.commerce.core.id.UuidV7Generator;
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

/** 회원의 발급분(issued coupon) 애그리거트 루트다. */
@Entity
@Table(schema = "coupon", name = "issued_coupon")
public class IssuedCoupon extends BaseTimeEntity<UUID> {

    /** 발급분 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 발급 근거인 쿠폰 정책 식별자. */
    @Column(name = "coupon_id")
    private UUID couponId;

    /** 발급 대상 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 발급분 사용 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IssuedCouponStatus status;

    /** 사용 기한. 발급 시각 + 정책의 사용 창(일). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** 사용 시각. 사용 상태일 때만 있다. */
    @Column(name = "used_at")
    @Nullable
    private Instant usedAt;

    /** 사용된 주문 식별자. order 도메인 논리 참조. 사용 상태일 때만 있다. */
    @Column(name = "order_id")
    @Nullable
    private UUID orderId;

    /** 무효화 시각. 무효화 상태일 때만 있다. */
    @Column(name = "revoked_at")
    @Nullable
    private Instant revokedAt;

    /** 무효화 사유. 자유 문자열. 무효화 상태일 때만 있다. */
    @Column(name = "revoke_reason")
    @Nullable
    private String revokeReason;

    /** 낙관락 버전. */
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
