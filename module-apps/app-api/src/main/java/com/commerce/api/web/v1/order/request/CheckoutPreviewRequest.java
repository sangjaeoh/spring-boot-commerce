package com.commerce.api.web.v1.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 체크아웃 미리보기 요청이다. 배송지·결제 수단은 금액에 영향이 없어 받지 않는다. */
@Schema(description = "체크아웃 미리보기(장바구니 전체·선택 → 결제 예정 금액) 요청")
public record CheckoutPreviewRequest(
        @Schema(description = "미리볼 장바구니 변형 ID 목록(1개 이상). 생략하면 장바구니 전체", nullable = true) @Nullable @Size(min = 1)
        List<@NotNull UUID> variantIds,

        @Schema(description = "배송비(원 단위)") @NotNull @PositiveOrZero
        Long shippingFee,

        @Schema(description = "적용할 발급 쿠폰 ID. 미적용이면 생략", nullable = true) @Nullable
        UUID issuedCouponId) {}
