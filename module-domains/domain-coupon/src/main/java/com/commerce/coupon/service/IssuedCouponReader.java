package com.commerce.coupon.service;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponNotFoundException;
import com.commerce.coupon.exception.IssuedCouponNotFoundException;
import com.commerce.coupon.info.IssuedCouponInfo;
import com.commerce.coupon.repository.CouponRepository;
import com.commerce.coupon.repository.IssuedCouponRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발급 쿠폰 조회와 할인액 산출을 담당한다. */
@Service
public class IssuedCouponReader {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;

    public IssuedCouponReader(IssuedCouponRepository issuedCouponRepository, CouponRepository couponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRepository = couponRepository;
    }

    /**
     * 본인 발급분을 조회한다. 미소유는 미존재로 취급한다.
     *
     * @throws IssuedCouponNotFoundException 본인 발급분이 없으면
     */
    @Transactional(readOnly = true)
    public IssuedCouponInfo getIssuedCoupon(UUID issuedCouponId, UUID memberId) {
        return issuedCouponRepository
                .findByIdAndMemberId(issuedCouponId, memberId)
                .map(IssuedCouponInfo::from)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
    }

    /** 회원의 발급 쿠폰 목록을 최신순으로 조회한다. 없으면 빈 목록이다. 같은 생성 시각은 id로 결정적 순서를 둔다. */
    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getIssuedCouponsByMember(UUID memberId) {
        return issuedCouponRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }

    /**
     * 발급분의 쿠폰 정책으로 주문 금액에 대한 할인액을 산출한다.
     *
     * @throws IssuedCouponNotFoundException 발급분이 없으면
     * @throws CouponNotFoundException 쿠폰 정책이 없으면
     */
    @Transactional(readOnly = true)
    public Money calculateDiscount(UUID issuedCouponId, Money orderAmount) {
        IssuedCoupon issued = issuedCouponRepository
                .findById(issuedCouponId)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
        Coupon coupon = couponRepository
                .findById(issued.getCouponId())
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
        return coupon.calculateDiscount(orderAmount);
    }
}
