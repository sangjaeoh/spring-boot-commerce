package com.commerce.coupon.application;

import com.commerce.coupon.application.info.DiscountPreviewInfo;
import com.commerce.coupon.application.info.IssuedCouponInfo;
import com.commerce.coupon.application.provided.IssuedCouponReader;
import com.commerce.coupon.application.required.CouponRepository;
import com.commerce.coupon.application.required.IssuedCouponRepository;
import com.commerce.coupon.domain.Coupon;
import com.commerce.coupon.domain.CouponErrorCode;
import com.commerce.coupon.domain.CouponNotFoundException;
import com.commerce.coupon.domain.IssuedCoupon;
import com.commerce.coupon.domain.IssuedCouponNotFoundException;
import com.commerce.shared.entity.Money;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link IssuedCouponReader}의 기본 구현이다. */
@Service
class DefaultIssuedCouponReader implements IssuedCouponReader {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    DefaultIssuedCouponReader(
            IssuedCouponRepository issuedCouponRepository, CouponRepository couponRepository, Clock clock) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public IssuedCouponInfo getIssuedCoupon(UUID issuedCouponId, UUID memberId) {
        return issuedCouponRepository
                .findByIdAndMemberId(issuedCouponId, memberId)
                .map(IssuedCouponInfo::from)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    @Override
    public List<IssuedCouponInfo> getIssuedCouponsByMember(UUID memberId) {
        return issuedCouponRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<IssuedCouponInfo> getIssuedCouponsByCoupon(UUID couponId, Pageable pageable) {
        return issuedCouponRepository
                .findByCouponIdOrderByCreatedAtDescIdDesc(couponId, pageable)
                .map(IssuedCouponInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
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
