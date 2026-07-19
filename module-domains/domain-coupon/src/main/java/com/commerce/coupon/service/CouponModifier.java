package com.commerce.coupon.service;

import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponNotFoundException;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.repository.CouponRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 정책 발급 가능·중지 전환을 담당한다. */
@Service
public class CouponModifier {

    private final CouponRepository couponRepository;

    public CouponModifier(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * 발급을 중지한다.
     *
     * @throws CouponStatusException 발급 가능 상태가 아니면
     */
    @Transactional
    public void disable(UUID couponId) {
        find(couponId).disable();
    }

    /**
     * 발급을 재개한다.
     *
     * @throws CouponStatusException 발급 중지 상태가 아니면
     */
    @Transactional
    public void enable(UUID couponId) {
        find(couponId).enable();
    }

    private Coupon find(UUID couponId) {
        return couponRepository
                .findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
    }
}
