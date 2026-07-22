package com.commerce.coupon.application;

import com.commerce.coupon.application.provided.CouponModifier;
import com.commerce.coupon.application.required.CouponRepository;
import com.commerce.coupon.domain.Coupon;
import com.commerce.coupon.domain.CouponErrorCode;
import com.commerce.coupon.domain.CouponNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CouponModifier}의 기본 구현이다. */
@Service
class DefaultCouponModifier implements CouponModifier {

    private final CouponRepository couponRepository;

    DefaultCouponModifier(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional
    @Override
    public void disable(UUID couponId) {
        find(couponId).disable();
    }

    @Transactional
    @Override
    public void enable(UUID couponId) {
        find(couponId).enable();
    }

    /** 쿠폰 정책을 찾고 없으면 거부한다. */
    private Coupon find(UUID couponId) {
        return couponRepository
                .findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
    }
}
