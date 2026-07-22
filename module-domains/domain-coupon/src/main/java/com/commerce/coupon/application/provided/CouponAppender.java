package com.commerce.coupon.application.provided;

import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.InvalidCouponException;
import com.commerce.coupon.domain.ValidityPeriod;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 생성을 담당하는 서비스다. */
public interface CouponAppender {

    /**
     * 쿠폰 정책을 발급 가능 상태로 생성하고 새 쿠폰 ID를 반환한다. 발급 한도는 선택이며 없으면 무제한이다.
     *
     * @throws InvalidCouponException 사용 유효일수가 1 미만이거나 발급 한도가 1 미만이면
     */
    UUID create(
            String name,
            Discount discount,
            Money minOrderAmount,
            ValidityPeriod validity,
            int usageValidDays,
            @Nullable Integer maxIssuance);
}
