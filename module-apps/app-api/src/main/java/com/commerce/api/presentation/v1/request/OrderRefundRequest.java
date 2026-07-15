package com.commerce.api.presentation.v1.request;

import com.commerce.order.entity.RefundReason;
import jakarta.validation.constraints.NotNull;

/** 배송 완료 주문의 전체 반품 환불 요청이다. */
public record OrderRefundRequest(@NotNull RefundReason reason) {}
