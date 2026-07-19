package com.commerce.api.web.v1.order.request;

import com.commerce.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 회원은 토큰 주체에서 도출하고, 단가·할인·결제금액은 서버가 계산한다. */
@Schema(description = "체크아웃(장바구니 전체 → 주문·결제) 요청")
public record CheckoutRequest(
        @Schema(description = "배송지") @NotNull @Valid AddressRequest shippingAddress,

        @Schema(description = "배송비(원 단위)") @NotNull @PositiveOrZero
        Long shippingFee,

        @Schema(description = "적용할 발급 쿠폰 ID. 미적용이면 생략", nullable = true) @Nullable
        UUID issuedCouponId,

        @Schema(description = "결제 수단. 결제 금액이 0원이면 생략 가능", nullable = true) @Nullable
        PaymentMethod method) {}
