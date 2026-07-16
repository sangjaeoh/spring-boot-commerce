package com.commerce.coupon.service;

import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponExpiredException;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.exception.IssuedCouponNotFoundException;
import com.commerce.coupon.repository.IssuedCouponRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발급 쿠폰 사용·복원·무효화를 담당한다. */
@Service
public class IssuedCouponModifier {

    private final IssuedCouponRepository issuedCouponRepository;

    public IssuedCouponModifier(IssuedCouponRepository issuedCouponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
    }

    /**
     * 발급분을 사용 처리한다. 동시 사용은 낙관락으로 직렬화된다.
     *
     * @throws IssuedCouponNotFoundException 발급분이 없으면
     * @throws CouponStatusException 이미 사용된 발급분이면
     * @throws CouponExpiredException 사용 기한이 지났으면
     */
    @Transactional
    public void use(UUID issuedCouponId, UUID orderId) {
        find(issuedCouponId).use(orderId);
    }

    /** 사용을 복원한다. 사용 상태가 아니면 아무 일도 하지 않는다. */
    @Transactional
    public void restoreUse(UUID issuedCouponId) {
        find(issuedCouponId).restoreUse();
    }

    /**
     * 발급분을 무효화한다. 무효화된 발급분은 사용이 거부된다.
     *
     * @throws IssuedCouponNotFoundException 발급분이 없으면
     * @throws CouponStatusException 미사용({@code ISSUED}) 상태가 아니면
     */
    @Transactional
    public void revoke(UUID issuedCouponId, String reason) {
        find(issuedCouponId).revoke(reason);
    }

    private IssuedCoupon find(UUID issuedCouponId) {
        return issuedCouponRepository
                .findById(issuedCouponId)
                .orElseThrow(() -> new IssuedCouponNotFoundException(CouponErrorCode.ISSUED_COUPON_NOT_FOUND));
    }
}
