package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotBlank;

/** 발급 쿠폰 무효화 요청이다. 사유가 필수다. */
public record IssuedCouponRevocationRequest(@NotBlank String reason) {}
