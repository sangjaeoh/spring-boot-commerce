package com.commerce.coupon.service;

import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponExhaustedException;
import com.commerce.coupon.exception.CouponNotFoundException;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.exception.DuplicateIssuanceException;
import com.commerce.coupon.repository.CouponRepository;
import com.commerce.coupon.repository.IssuedCouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 발급을 담당한다. */
@Service
public class IssuedCouponAppender {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final Clock clock;

    public IssuedCouponAppender(
            CouponRepository couponRepository, IssuedCouponRepository issuedCouponRepository, Clock clock) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.clock = clock;
    }

    /**
     * 회원에게 쿠폰을 발급하고 새 발급분 ID를 반환한다. 회원 자격은 검증하지 않는다.
     *
     * <ol>
     *   <li>쿠폰 발급 가능 검사(ACTIVE·발급 기간)
     *   <li>회원당 동일 쿠폰 중복 검사
     *   <li>발급 한도가 있으면 원자적 조건부 UPDATE로 카운트 선점(0행이면 소진)
     *   <li>사용 기한 확정 후 발급분 저장
     * </ol>
     *
     * <p>선점과 발급분 저장이 한 트랜잭션이라 저장 실패 시 선점이 함께 롤백된다(슬롯 누수 없음).
     *
     * @throws CouponNotFoundException 쿠폰이 없으면
     * @throws CouponStatusException 발급 중지되었거나 발급 기간 밖이면
     * @throws DuplicateIssuanceException 회원에게 이미 발급된 쿠폰이면
     * @throws CouponExhaustedException 발급 한도가 소진됐으면
     */
    @Transactional
    public UUID issue(UUID couponId, UUID memberId) {
        Coupon coupon = couponRepository
                .findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
        Instant now = clock.instant();
        coupon.checkIssuable(now);
        if (issuedCouponRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
            throw new DuplicateIssuanceException(CouponErrorCode.DUPLICATE_ISSUANCE);
        }
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
