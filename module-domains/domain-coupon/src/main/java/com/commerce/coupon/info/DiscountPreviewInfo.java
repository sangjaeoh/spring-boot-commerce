package com.commerce.coupon.info;

import com.commerce.core.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * 발급 쿠폰 할인 미리보기 경계 모델이다. 적용 불가는 오류가 아니라 사유와 0원으로 표현한다.
 *
 * @param applicable 조회 시점 기준 적용 가능 여부
 * @param reason 적용 불가 사유(적용 가능하면 없음)
 * @param discountAmount 예상 할인액(적용 불가면 0)
 */
public record DiscountPreviewInfo(
        boolean applicable, @Nullable Reason reason, Money discountAmount) {

    public DiscountPreviewInfo {
        if (applicable != (reason == null) || applicable == discountAmount.isZero()) {
            throw new IllegalArgumentException("적용 가능이면 0 초과 할인액만, 적용 불가면 사유와 0원만 있어야 한다");
        }
    }

    public static DiscountPreviewInfo applicable(Money discountAmount) {
        return new DiscountPreviewInfo(true, null, discountAmount);
    }

    public static DiscountPreviewInfo notApplicable(Reason reason) {
        return new DiscountPreviewInfo(false, reason, Money.ZERO);
    }

    /** 적용 불가 사유다. */
    public enum Reason {
        ALREADY_USED,
        REVOKED,
        EXPIRED,
        MIN_ORDER_AMOUNT_NOT_MET,
        ZERO_DISCOUNT
    }
}
