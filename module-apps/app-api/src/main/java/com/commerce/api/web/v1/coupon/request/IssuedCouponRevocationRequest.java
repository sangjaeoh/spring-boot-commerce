package com.commerce.api.web.v1.coupon.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 발급 쿠폰 무효화 요청이다. 사유가 필수다. */
@Schema(description = "발급 쿠폰 무효화 요청")
public record IssuedCouponRevocationRequest(
        @Schema(description = "무효화 사유") @NotBlank String reason) {}
