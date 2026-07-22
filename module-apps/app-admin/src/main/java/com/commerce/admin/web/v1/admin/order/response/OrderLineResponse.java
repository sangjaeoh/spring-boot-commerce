package com.commerce.admin.web.v1.admin.order.response;

import com.commerce.order.application.info.OrderLineInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// app-api 본인/공개 슬라이스의 동명 응답과 같은 형상의 어드민 소유 사본이다(와이어 계약 동일).
@Schema(description = "주문 라인 응답. 단가·옵션은 주문 시점 스냅샷이다.")
public record OrderLineResponse(
        @Schema(description = "변형(SKU) ID") UUID variantId,
        @Schema(description = "상품 ID") UUID productId,
        @Schema(description = "상품명(주문 시점 스냅샷)") String productName,

        @Schema(description = "옵션 표기(주문 시점 스냅샷). 옵션이 없으면 생략", nullable = true) @Nullable
        String optionLabel,

        @Schema(description = "단가(원 단위, 주문 시점 스냅샷)") long unitPrice,
        @Schema(description = "수량") int quantity) {

    /** 주문 라인 조회 모델에서 응답을 만든다. */
    public static OrderLineResponse from(OrderLineInfo line) {
        return new OrderLineResponse(
                line.variantId(),
                line.productId(),
                line.productName(),
                line.optionLabel(),
                line.unitPrice().amount(),
                line.quantity());
    }
}
