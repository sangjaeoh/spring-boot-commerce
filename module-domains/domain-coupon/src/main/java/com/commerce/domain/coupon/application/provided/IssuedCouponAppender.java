package com.commerce.domain.coupon.application.provided;

import com.commerce.domain.coupon.domain.exception.CouponExhaustedException;
import com.commerce.domain.coupon.domain.exception.CouponNotFoundException;
import com.commerce.domain.coupon.domain.exception.CouponStatusException;
import com.commerce.domain.coupon.domain.exception.DuplicateIssuanceException;
import java.util.UUID;

/** 쿠폰 발급을 담당하는 서비스다. */
public interface IssuedCouponAppender {

    /**
     * 회원에게 쿠폰을 발급하고 새 발급분 ID를 반환한다. 회원 자격은 검증하지 않는다.
     *
     * @throws CouponNotFoundException 쿠폰이 없으면
     * @throws CouponStatusException 발급 중지되었거나 발급 기간 밖이면
     * @throws DuplicateIssuanceException 회원에게 이미 발급된 쿠폰이면
     * @throws CouponExhaustedException 발급 한도가 소진됐으면
     */
    UUID issue(UUID couponId, UUID memberId);
}
