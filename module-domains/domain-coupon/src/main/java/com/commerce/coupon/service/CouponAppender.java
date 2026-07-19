package com.commerce.coupon.service;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.InvalidCouponException;
import com.commerce.coupon.repository.CouponRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 정책 생성을 담당한다. */
@Service
public class CouponAppender {

    private final CouponRepository couponRepository;

    public CouponAppender(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * 쿠폰 정책을 발급 가능 상태로 생성하고 새 쿠폰 ID를 반환한다. 발급 한도는 선택이며 없으면 무제한이다.
     *
     * @throws InvalidCouponException 사용 유효일수가 1 미만이거나 발급 한도가 1 미만이면
     */
    @Transactional
    public UUID create(
            String name,
            Discount discount,
            Money minOrderAmount,
            ValidityPeriod validity,
            int usageValidDays,
            @Nullable Integer maxIssuance) {
        Coupon coupon = Coupon.create(name, discount, minOrderAmount, validity, usageValidDays, maxIssuance);
        return couponRepository.save(coupon).getId();
    }
}
