package com.commerce.admin.web.v1.admin.order.request;

import com.commerce.order.domain.HoldReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 이행 보류 요청")
public record FulfillmentHoldRequest(
        @Schema(description = "이행 보류 사유") @NotNull HoldReason reason) {}
