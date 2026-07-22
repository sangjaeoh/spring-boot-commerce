package com.commerce.payment.adapter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.payment.application.required.GatewayTransactionStatus;
import com.commerce.payment.application.required.PaymentApproval;
import com.commerce.payment.domain.FailureReason;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.shared.entity.Money;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();

    @Test
    @DisplayName("트리거 금액이 아니면 승인하고 상태 조회가 같은 거래를 재생한다")
    void approveRecordsTransactionForInquiry() {
        UUID paymentId = UUID.randomUUID();

        PaymentApproval result = gateway.approve(paymentId, Money.of(10_000L), PaymentMethod.CARD);

        assertThat(result.approved()).isTrue();
        GatewayTransactionStatus inquiry = gateway.inquire(paymentId);
        assertThat(inquiry.result()).isEqualTo(GatewayTransactionStatus.Result.APPROVED);
        assertThat(inquiry.pgTransactionId()).isEqualTo(result.pgTransactionId());
    }

    @Test
    @DisplayName("끝 세 자리 999 금액은 잔액 부족으로 거절하고 거절 거래를 보관한다")
    void declineTriggerDeclines() {
        UUID paymentId = UUID.randomUUID();

        PaymentApproval result = gateway.approve(paymentId, Money.of(10_999L), PaymentMethod.CARD);

        assertThat(result.approved()).isFalse();
        assertThat(result.failureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
        GatewayTransactionStatus inquiry = gateway.inquire(paymentId);
        assertThat(inquiry.result()).isEqualTo(GatewayTransactionStatus.Result.DECLINED);
        assertThat(inquiry.failureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("끝 세 자리 998 금액은 승인 거래를 기록한 채 응답을 유실한다")
    void lostResponseTriggerRecordsApprovalThenThrows() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> gateway.approve(paymentId, Money.of(10_998L), PaymentMethod.CARD))
                .isInstanceOf(FakeGatewayTimeoutException.class);

        GatewayTransactionStatus inquiry = gateway.inquire(paymentId);
        assertThat(inquiry.result()).isEqualTo(GatewayTransactionStatus.Result.APPROVED);
        assertThat(inquiry.pgTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("모르는 결제의 상태 조회는 미도달이다")
    void inquireUnknownPaymentReturnsNotFound() {
        assertThat(gateway.inquire(UUID.randomUUID()).result()).isEqualTo(GatewayTransactionStatus.Result.NOT_FOUND);
    }

    @Test
    @DisplayName("같은 멱등 키의 반복 취소는 최초 취소 거래 ID를 재생한다")
    void cancelIsIdempotentByKey() {
        UUID paymentId = UUID.randomUUID();
        String pgTransactionId =
                Objects.requireNonNull(gateway.approve(paymentId, Money.of(10_000L), PaymentMethod.CARD)
                        .pgTransactionId());

        String first = gateway.cancel(pgTransactionId, "CANCEL:" + pgTransactionId);
        String second = gateway.cancel(pgTransactionId, "CANCEL:" + pgTransactionId);
        String other = gateway.cancel(pgTransactionId, "CANCEL:other");

        assertThat(second).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }
}
