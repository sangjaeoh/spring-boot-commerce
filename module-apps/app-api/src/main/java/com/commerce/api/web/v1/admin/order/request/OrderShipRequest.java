package com.commerce.api.web.v1.admin.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 주문 출고 요청이다. 택배사·운송장 번호가 필수다. */
@Schema(description = "주문 출고 요청")
public record OrderShipRequest(
        @Schema(description = "택배사") @NotBlank String carrier,
        @Schema(description = "운송장 번호") @NotBlank String trackingNumber) {}
