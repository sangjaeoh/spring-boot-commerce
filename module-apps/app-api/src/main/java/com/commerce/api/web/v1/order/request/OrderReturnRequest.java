package com.commerce.api.web.v1.order.request;

import com.commerce.order.domain.RefundReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "배송 완료 주문의 반품 요청")
public record OrderReturnRequest(
        @Schema(description = "반품 요청 사유") @NotNull RefundReason reason) {}
