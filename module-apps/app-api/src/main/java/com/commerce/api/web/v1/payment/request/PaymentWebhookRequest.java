package com.commerce.api.web.v1.payment.request;

import java.util.Objects;
import java.util.UUID;

/** PG 결제 확정 통지 페이로드다. 결과가 아니라 확정할 결제만 지목한다(확정 근거는 PG 상태 조회). */
public record PaymentWebhookRequest(UUID paymentId) {

    public PaymentWebhookRequest {
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
