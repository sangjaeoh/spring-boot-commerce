package com.commerce.coupon.application.provided;

import com.commerce.coupon.domain.CouponNotFoundException;
import com.commerce.coupon.domain.CouponStatusException;
import java.util.UUID;

/** 쿠폰 정책 발급 가능·중지 전환을 담당하는 서비스다. */
public interface CouponModifier {

    /**
     * 발급을 중지한다.
     *
     * @throws CouponNotFoundException 쿠폰이 없으면
     * @throws CouponStatusException 발급 가능 상태가 아니면
     */
    void disable(UUID couponId);

    /**
     * 발급을 재개한다.
     *
     * @throws CouponNotFoundException 쿠폰이 없으면
     * @throws CouponStatusException 발급 중지 상태가 아니면
     */
    void enable(UUID couponId);
}
