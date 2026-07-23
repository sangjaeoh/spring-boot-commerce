package com.commerce.domain.coupon.application.provided;

import com.commerce.domain.coupon.application.info.CouponInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 쿠폰 정책 조회를 담당하는 서비스다. */
public interface CouponReader {

    /** 쿠폰 정책 목록을 최신 등록순 페이지로 조회한다. 없으면 빈 페이지다. */
    Page<CouponInfo> getCoupons(Pageable pageable);

    /** 발급 가능한(활성·기간 내·한도 미소진) 쿠폰 정책 목록을 최신 등록순 페이지로 조회한다. 없으면 빈 페이지다. */
    Page<CouponInfo> getIssuableCoupons(Pageable pageable);
}
