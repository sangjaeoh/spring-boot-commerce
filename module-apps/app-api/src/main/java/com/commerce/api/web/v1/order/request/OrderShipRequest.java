package com.commerce.api.web.v1.order.request;

import jakarta.validation.constraints.NotBlank;

/** 주문 출고 요청이다. 택배사·운송장 번호가 필수다. */
public record OrderShipRequest(
        @NotBlank String carrier, @NotBlank String trackingNumber) {}
