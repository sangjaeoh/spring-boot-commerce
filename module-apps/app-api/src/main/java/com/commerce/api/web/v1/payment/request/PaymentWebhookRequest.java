package com.commerce.api.web.v1.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** 결과가 아니라 확정할 결제만 지목한다(확정 근거는 PG 상태 조회). */
@Schema(description = "PG 결제 확정 통지 페이로드")
public record PaymentWebhookRequest(
        @Schema(description = "확정할 결제 식별자") UUID paymentId) {

    public PaymentWebhookRequest {
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
