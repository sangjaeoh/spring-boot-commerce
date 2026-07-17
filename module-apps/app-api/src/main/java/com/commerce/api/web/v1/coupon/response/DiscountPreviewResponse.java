package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.DiscountPreviewInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** 발급 쿠폰 할인 미리보기 응답이다. 적용 불가는 오류가 아니라 사유와 0원으로 싣는다. */
@Schema(description = "발급 쿠폰 할인 미리보기 응답")
public record DiscountPreviewResponse(
        @Schema(description = "적용 가능 여부") boolean applicable,

        @Schema(description = "적용 불가 사유(적용 가능이면 없음)", nullable = true)
        DiscountPreviewInfo.@Nullable Reason reason,

        @Schema(description = "예상 할인액(원 단위, 적용 불가면 0)") long discountAmount) {

    public static DiscountPreviewResponse from(DiscountPreviewInfo preview) {
        return new DiscountPreviewResponse(
                preview.applicable(), preview.reason(), preview.discountAmount().amount());
    }
}
