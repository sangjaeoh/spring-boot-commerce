package com.commerce.app.admin.web.v1.admin.coupon.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "발급 쿠폰 무효화 요청")
public record IssuedCouponRevocationRequest(
        @Schema(description = "무효화 사유") @NotBlank String reason) {}
