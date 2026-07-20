package com.commerce.coupon.service;

import com.commerce.coupon.info.CouponInfo;
import com.commerce.coupon.repository.CouponRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 정책 조회를 담당하는 서비스다. */
@Service
public class CouponReader {

    private final CouponRepository couponRepository;

    public CouponReader(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /** 쿠폰 정책 목록을 최신 등록순 페이지로 조회한다. 없으면 빈 페이지다. */
    @Transactional(readOnly = true)
    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponRepository.findPage(pageable).map(CouponInfo::from);
    }
}
