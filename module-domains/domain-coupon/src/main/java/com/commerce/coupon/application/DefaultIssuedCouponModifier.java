package com.commerce.coupon.application;

import com.commerce.coupon.application.provided.IssuedCouponModifier;
import com.commerce.coupon.application.required.IssuedCouponRepository;
import com.commerce.coupon.domain.CouponErrorCode;
import com.commerce.coupon.domain.IssuedCoupon;
import com.commerce.coupon.domain.IssuedCouponNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link IssuedCouponModifier}의 기본 구현이다. */
@Service
class DefaultIssuedCouponModifier implements IssuedCouponModifier {

    private final IssuedCouponRepository issuedCouponRepository;
    private final Clock clock;

    DefaultIssuedCouponModifier(IssuedCouponRepository issuedCouponRepository, Clock clock) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void use(UUID issuedCouponId, UUID memberId, UUID orderId) {
        findOwned(issuedCouponId, memberId).use(orderId, clock.instant());
    }

    @Transactional
    @Override
    public void restoreUse(UUID issuedCouponId, UUID orderId) {
        find(issuedCouponId).restoreUse(orderId);
    }

    @Transactional
    @Override
    public void revoke(UUID issuedCouponId, String reason) {
        find(issuedCouponId).revoke(reason, clock.instant());
    }

    /** 발급분을 찾고 없으면 거부한다. */
    private IssuedCoupon find(UUID issuedCouponId) {
        return issuedCouponRepository
                .findById(issuedCouponId)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
    }

    /** 본인 소유 발급분을 찾고 없으면 거부한다. */
    private IssuedCoupon findOwned(UUID issuedCouponId, UUID memberId) {
        return issuedCouponRepository
                .findByIdAndMemberId(issuedCouponId, memberId)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
    }
}
