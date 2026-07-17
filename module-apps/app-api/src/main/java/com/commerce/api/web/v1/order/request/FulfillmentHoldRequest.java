package com.commerce.api.web.v1.order.request;

import com.commerce.order.entity.HoldReason;
import jakarta.validation.constraints.NotNull;

/** 주문 이행 보류 요청이다. */
public record FulfillmentHoldRequest(@NotNull HoldReason reason) {}
