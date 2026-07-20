package com.commerce.api.web.v1.payment.response;

import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 전액 할인(0원) 결제는 PG를 생략하므로 수단·승인 거래 ID가 비어 있을 수 있다. */
@Schema(description = "주문 결제 응답")
public record PaymentResponse(
        @Schema(description = "결제 상태") PaymentStatus status,

        @Schema(description = "결제 수단", nullable = true) @Nullable
        PaymentMethod method,

        @Schema(description = "결제 금액(원 단위)") long amount,

        @Schema(description = "실패 사유", nullable = true) @Nullable
        FailureReason failureReason,

        @Schema(description = "PG 승인 거래 ID", nullable = true) @Nullable
        String pgTransactionId,

        @Schema(description = "PG 취소 거래 ID", nullable = true) @Nullable
        String pgCancelTransactionId,

        @Schema(description = "승인 시각", nullable = true) @Nullable
        Instant approvedAt,

        @Schema(description = "취소 시각", nullable = true) @Nullable
        Instant cancelledAt) {

    /** 결제 조회 모델에서 응답을 만든다. */
    public static PaymentResponse from(PaymentInfo payment) {
        return new PaymentResponse(
                payment.status(),
                payment.method(),
                payment.amount().amount(),
                payment.failureReason(),
                payment.pgTransactionId(),
                payment.pgCancelTransactionId(),
                payment.approvedAt(),
                payment.cancelledAt());
    }
}
