package com.commerce.api.presentation.v1.response;

import com.commerce.coupon.info.DiscountPreviewInfo;
import org.jspecify.annotations.Nullable;

/** 발급 쿠폰 할인 미리보기 응답이다. 적용 불가는 오류가 아니라 사유와 0원으로 싣는다. */
public record DiscountPreviewResponse(
        boolean applicable, DiscountPreviewInfo.@Nullable Reason reason, long discountAmount) {

    public static DiscountPreviewResponse from(DiscountPreviewInfo preview) {
        return new DiscountPreviewResponse(
                preview.applicable(), preview.reason(), preview.discountAmount().amount());
    }
}
