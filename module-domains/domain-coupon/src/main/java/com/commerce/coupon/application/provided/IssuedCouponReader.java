package com.commerce.coupon.application.provided;

import com.commerce.coupon.application.info.DiscountPreviewInfo;
import com.commerce.coupon.application.info.IssuedCouponInfo;
import com.commerce.coupon.domain.CouponNotFoundException;
import com.commerce.coupon.domain.IssuedCouponNotFoundException;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 발급분 조회와 할인액 산출을 담당하는 서비스다. */
public interface IssuedCouponReader {

    /**
     * 본인 발급분을 조회한다. 미소유는 미존재로 취급한다.
     *
     * @throws IssuedCouponNotFoundException 본인 발급분이 없으면
     */
    IssuedCouponInfo getIssuedCoupon(UUID issuedCouponId, UUID memberId);

    /** 회원의 발급분 목록을 최신순으로 조회한다. 없으면 빈 목록이다. 같은 생성 시각은 id로 결정적 순서를 둔다. */
    List<IssuedCouponInfo> getIssuedCouponsByMember(UUID memberId);

    /**
     * 쿠폰 정책의 발급분 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로 결정적 순서를 둔다.
     *
     * <p>소유 회원을 거르지 않으므로 관리자 가드 뒤에서만 부른다.
     */
    Page<IssuedCouponInfo> getIssuedCouponsByCoupon(UUID couponId, Pageable pageable);

    /**
     * 본인 발급분에 대해 주문 금액 기준 예상 할인을 산출한다. 계산만 하고 상태를 바꾸지 않으며, 적용 불가는
     * 예외가 아니라 사유와 0원으로 표현한다. 미소유는 미존재로 취급한다. 결과는 보증이 아니며 체크아웃 시점
     * 재검증이 진실이다.
     *
     * @throws IssuedCouponNotFoundException 본인 발급분이 없으면
     * @throws CouponNotFoundException 쿠폰 정책이 없으면
     */
    DiscountPreviewInfo getDiscountPreview(UUID issuedCouponId, UUID memberId, Money orderAmount);
}
