package com.commerce.api.web.v1.cart.response;

import com.commerce.api.facade.view.CartLineView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "장바구니 라인 응답")
public record CartLineResponse(
        @Schema(description = "상품 변형 ID") UUID variantId,

        @Schema(description = "옵션 표시 라벨", nullable = true) @Nullable
        String optionLabel,

        @Schema(description = "변형 현재 단가") long unitPrice,
        @Schema(description = "라인 수량") int quantity,
        @Schema(description = "라인 소계(단가×수량)") long subtotal,
        @Schema(description = "주문 가능 여부") boolean orderable) {

    /** 장바구니 라인 뷰에서 응답을 만든다. */
    public static CartLineResponse from(CartLineView line) {
        return new CartLineResponse(
                line.variantId(),
                line.optionLabel(),
                line.unitPrice().amount(),
                line.quantity(),
                line.subtotal().amount(),
                line.orderable());
    }
}
