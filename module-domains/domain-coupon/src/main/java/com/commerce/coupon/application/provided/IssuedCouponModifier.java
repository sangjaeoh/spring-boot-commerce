package com.commerce.coupon.application.provided;

import com.commerce.coupon.domain.CouponExpiredException;
import com.commerce.coupon.domain.CouponStatusException;
import com.commerce.coupon.domain.IssuedCouponNotFoundException;
import java.util.UUID;

/** 발급분 사용·복원·무효화를 담당하는 서비스다. */
public interface IssuedCouponModifier {

    /**
     * 발급분을 사용 처리한다. 본인({@code memberId}) 소유가 아니면 미존재로 취급해 거부한다.
     *
     * @throws IssuedCouponNotFoundException 발급분이 없거나 본인 소유가 아니면
     * @throws CouponStatusException 이미 사용된 발급분이면
     * @throws CouponExpiredException 사용 기한이 지났으면
     */
    void use(UUID issuedCouponId, UUID memberId, UUID orderId);

    /** 해당 주문에 대한 사용을 복원한다. 사용 상태가 아니거나 사용 주문이 다르면 아무 일도 하지 않는다. */
    void restoreUse(UUID issuedCouponId, UUID orderId);

    /**
     * 발급분을 무효화한다. 무효화된 발급분은 사용이 거부된다.
     *
     * @throws IssuedCouponNotFoundException 발급분이 없으면
     * @throws CouponStatusException 미사용({@code ISSUED}) 상태가 아니면
     */
    void revoke(UUID issuedCouponId, String reason);
}
