package com.commerce.coupon.application.info;

import com.commerce.shared.entity.Money;
import org.jspecify.annotations.Nullable;

/**
 * 발급분 할인 미리보기 경계 모델이다.
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

    /** 적용 가능한 미리보기를 만든다. */
    public static DiscountPreviewInfo applicable(Money discountAmount) {
        return new DiscountPreviewInfo(true, null, discountAmount);
    }

    /** 사유를 실은 적용 불가 미리보기를 만든다. */
    public static DiscountPreviewInfo notApplicable(Reason reason) {
        return new DiscountPreviewInfo(false, reason, Money.ZERO);
    }

    /** 적용 불가 사유다. */
    public enum Reason {
        /** 이미 사용된 발급분. */
        ALREADY_USED,
        /** 무효화된 발급분. */
        REVOKED,
        /** 사용 기한 경과. */
        EXPIRED,
        /** 최소 주문 금액 미달. */
        MIN_ORDER_AMOUNT_NOT_MET,
        /** 산출 할인액 0원. */
        ZERO_DISCOUNT
    }
}
