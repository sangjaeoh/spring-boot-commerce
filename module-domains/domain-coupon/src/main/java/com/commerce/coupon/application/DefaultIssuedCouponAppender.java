package com.commerce.coupon.application;

import com.commerce.coupon.application.provided.IssuedCouponAppender;
import com.commerce.coupon.application.required.CouponRepository;
import com.commerce.coupon.application.required.IssuedCouponRepository;
import com.commerce.coupon.domain.Coupon;
import com.commerce.coupon.domain.IssuedCoupon;
import com.commerce.coupon.domain.exception.CouponErrorCode;
import com.commerce.coupon.domain.exception.CouponExhaustedException;
import com.commerce.coupon.domain.exception.CouponNotFoundException;
import com.commerce.coupon.domain.exception.DuplicateIssuanceException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link IssuedCouponAppender}의 기본 구현이다. */
@Service
class DefaultIssuedCouponAppender implements IssuedCouponAppender {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final Clock clock;

    DefaultIssuedCouponAppender(
            CouponRepository couponRepository, IssuedCouponRepository issuedCouponRepository, Clock clock) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public UUID issue(UUID couponId, UUID memberId) {
        Coupon coupon = couponRepository
                .findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
        Instant now = clock.instant();
        coupon.checkIssuable(now);
        if (issuedCouponRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
            throw new DuplicateIssuanceException(CouponErrorCode.DUPLICATE_ISSUANCE);
        }
        // 선점과 발급분 저장이 한 트랜잭션이라 저장 실패가 선점을 함께 롤백한다(슬롯 누수 없음).
        if (coupon.hasIssuanceLimit() && couponRepository.claimIssuanceSlot(couponId) == 0) {
            throw new CouponExhaustedException(CouponErrorCode.ISSUANCE_LIMIT_EXHAUSTED);
        }
        Instant expiresAt = now.plus(coupon.getUsageValidDays(), ChronoUnit.DAYS);
        try {
            return issuedCouponRepository
                    .saveAndFlush(IssuedCoupon.create(couponId, memberId, expiresAt))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 발급 경합 방어
            throw new DuplicateIssuanceException(CouponErrorCode.DUPLICATE_ISSUANCE);
        }
    }
}
