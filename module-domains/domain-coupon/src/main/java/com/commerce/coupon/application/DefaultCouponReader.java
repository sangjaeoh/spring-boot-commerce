package com.commerce.coupon.application;

import com.commerce.coupon.application.info.CouponInfo;
import com.commerce.coupon.application.provided.CouponReader;
import com.commerce.coupon.application.required.CouponRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CouponReader}의 기본 구현이다. */
@Service
class DefaultCouponReader implements CouponReader {

    private final CouponRepository couponRepository;
    private final Clock clock;

    DefaultCouponReader(CouponRepository couponRepository, Clock clock) {
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponRepository.findPage(pageable).map(CouponInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<CouponInfo> getIssuableCoupons(Pageable pageable) {
        return couponRepository.findIssuablePage(clock.instant(), pageable).map(CouponInfo::from);
    }
}
