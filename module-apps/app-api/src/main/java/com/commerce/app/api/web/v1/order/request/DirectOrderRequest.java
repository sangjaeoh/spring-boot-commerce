package com.commerce.app.api.web.v1.order.request;

import com.commerce.app.api.facade.DirectOrderLine;
import com.commerce.domain.payment.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 바로구매 요청이다. 장바구니를 거치지 않고, 주문 회원은 토큰 주체에서 도출하며 단가·할인·결제금액은 서버가 계산한다. */
@Schema(description = "바로구매(요청 라인 → 주문·결제) 요청")
public record DirectOrderRequest(
        @Schema(description = "주문 라인 목록(1개 이상)") @NotEmpty @Valid
        List<@NotNull DirectOrderItemRequest> items,

        @Schema(description = "배송지") @NotNull @Valid AddressRequest shippingAddress,

        @Schema(description = "배송비(원 단위)") @NotNull @PositiveOrZero
        Long shippingFee,

        @Schema(description = "적용할 발급 쿠폰 ID. 미적용이면 생략", nullable = true) @Nullable
        UUID issuedCouponId,

        @Schema(description = "결제 수단. 결제 금액이 0원이면 생략 가능", nullable = true) @Nullable
        PaymentMethod method) {

    /** 요청 라인들을 파사드 입력 라인으로 변환한다. */
    public List<DirectOrderLine> toLines() {
        return items.stream()
                .map(item -> new DirectOrderLine(item.variantId(), item.quantity()))
                .toList();
    }
}
