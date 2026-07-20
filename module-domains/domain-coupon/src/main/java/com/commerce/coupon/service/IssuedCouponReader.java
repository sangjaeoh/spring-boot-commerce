package com.commerce.coupon.service;

import com.commerce.coupon.entity.Coupon;
import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponNotFoundException;
import com.commerce.coupon.exception.IssuedCouponNotFoundException;
import com.commerce.coupon.info.DiscountPreviewInfo;
import com.commerce.coupon.info.IssuedCouponInfo;
import com.commerce.coupon.repository.CouponRepository;
import com.commerce.coupon.repository.IssuedCouponRepository;
import com.commerce.shared.entity.Money;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발급 쿠폰 조회와 할인액 산출을 담당한다. */
@Service
public class IssuedCouponReader {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    public IssuedCouponReader(
            IssuedCouponRepository issuedCouponRepository, CouponRepository couponRepository, Clock clock) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
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
     * 쿠폰 정책의 발급분 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로 결정적 순서를 둔다.
     *
     * <p>소유 회원을 거르지 않으므로 관리자 가드 뒤에서만 부른다.
     */
    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getIssuedCouponsByCoupon(UUID couponId, Pageable pageable) {
        return issuedCouponRepository
                .findByCouponIdOrderByCreatedAtDescIdDesc(couponId, pageable)
                .map(IssuedCouponInfo::from);
    }

    /**
     * 본인 발급분에 대해 주문 금액 기준 예상 할인을 산출한다. 계산만 하고 상태를 바꾸지 않으며, 적용 불가는
     * 예외가 아니라 사유와 0원으로 표현한다. 미소유는 미존재로 취급한다. 결과는 보증이 아니며 체크아웃 시점
     * 재검증이 진실이다.
     *
     * @throws IssuedCouponNotFoundException 본인 발급분이 없으면
     * @throws CouponNotFoundException 쿠폰 정책이 없으면
     */
    @Transactional(readOnly = true)
    public DiscountPreviewInfo getDiscountPreview(UUID issuedCouponId, UUID memberId, Money orderAmount) {
        IssuedCoupon issued = issuedCouponRepository
                .findByIdAndMemberId(issuedCouponId, memberId)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
        DiscountPreviewInfo.Reason statusReason =
                switch (issued.getStatus()) {
                    case ISSUED -> null;
                    case USED -> DiscountPreviewInfo.Reason.ALREADY_USED;
                    case REVOKED -> DiscountPreviewInfo.Reason.REVOKED;
                };
        if (statusReason != null) {
            return DiscountPreviewInfo.notApplicable(statusReason);
        }
        if (issued.getExpiresAt().isBefore(clock.instant())) {
            return DiscountPreviewInfo.notApplicable(DiscountPreviewInfo.Reason.EXPIRED);
        }
        Coupon coupon = couponRepository
                .findById(issued.getCouponId())
                .orElseThrow(() -> new CouponNotFoundException(CouponErrorCode.COUPON_NOT_FOUND));
        Money discount = coupon.calculateDiscount(orderAmount);
        if (discount.isZero()) {
            return DiscountPreviewInfo.notApplicable(
                    coupon.isMinOrderAmountMet(orderAmount)
                            ? DiscountPreviewInfo.Reason.ZERO_DISCOUNT
                            : DiscountPreviewInfo.Reason.MIN_ORDER_AMOUNT_NOT_MET);
        }
        return DiscountPreviewInfo.applicable(discount);
    }
}
