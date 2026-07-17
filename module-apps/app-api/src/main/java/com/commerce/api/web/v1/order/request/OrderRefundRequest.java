package com.commerce.api.web.v1.order.request;

import com.commerce.order.entity.RefundReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 배송 완료 주문의 전체 반품 환불 요청이다. */
@Schema(description = "배송 완료 주문의 전체 반품 환불 요청")
public record OrderRefundRequest(
        @Schema(description = "반품 환불 사유") @NotNull RefundReason reason) {}
