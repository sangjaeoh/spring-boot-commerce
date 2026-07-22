package com.commerce.admin.web.v1.admin.order.request;

import com.commerce.order.domain.RefundReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "배송 완료 주문의 전체 반품 환불 요청")
public record OrderRefundRequest(
        @Schema(description = "반품 환불 사유") @NotNull RefundReason reason) {}
