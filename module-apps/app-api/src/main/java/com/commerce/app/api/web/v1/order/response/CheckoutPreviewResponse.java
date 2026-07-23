package com.commerce.app.api.web.v1.order.response;

import com.commerce.app.api.facade.view.CheckoutPreviewView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "체크아웃 미리보기 응답")
public record CheckoutPreviewResponse(
        @Schema(description = "상품 합계 금액(할인 전, 원 단위)") long totalAmount,
        @Schema(description = "쿠폰 할인액(원 단위, 미적용이면 0)") long discountAmount,
        @Schema(description = "배송비(원 단위)") long shippingFee,
        @Schema(description = "결제 예정액(합계 - 할인 + 배송비, 원 단위)") long payAmount) {

    /** 미리보기 뷰에서 응답을 만든다. */
    public static CheckoutPreviewResponse from(CheckoutPreviewView view) {
        return new CheckoutPreviewResponse(
                view.totalAmount().amount(),
                view.discountAmount().amount(),
                view.shippingFee().amount(),
                view.payAmount().amount());
    }
}
