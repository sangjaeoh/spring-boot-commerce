package com.commerce.coupon.application;

import com.commerce.coupon.application.provided.CouponAppender;
import com.commerce.coupon.application.required.CouponRepository;
import com.commerce.coupon.domain.Coupon;
import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.ValidityPeriod;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CouponAppender}의 기본 구현이다. */
@Service
class DefaultCouponAppender implements CouponAppender {

    private final CouponRepository couponRepository;

    DefaultCouponAppender(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional
    @Override
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
