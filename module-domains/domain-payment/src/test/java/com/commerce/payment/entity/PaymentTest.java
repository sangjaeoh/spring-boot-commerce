package com.commerce.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.core.money.Money;
import com.commerce.payment.exception.PaymentStatusException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private Payment requested() {
        return Payment.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
    }

    @Test
    @DisplayName("금액이 있으면 수단과 함께 요청된다")
    void requestWithAmountAndMethod() {
        Payment payment = requested();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.requiresGatewayApproval()).isTrue();
    }

    @Test
    @DisplayName("금액이 0이면 수단 없이 요청되고 PG 승인이 필요 없다")
    void requestZeroAmountWithoutMethod() {
        Payment payment = Payment.request(UUID.randomUUID(), Money.ZERO, null);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getMethod()).isNull();
        assertThat(payment.requiresGatewayApproval()).isFalse();
    }

    @Test
    @DisplayName("금액과 수단의 존재가 불일치하면 거부한다")
    void rejectsAmountMethodMismatch() {
        assertThatThrownBy(() -> Payment.request(UUID.randomUUID(), Money.of(10000L), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.request(UUID.randomUUID(), Money.ZERO, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인하면 APPROVED이고 거래 ID가 기록된다")
    void approveSetsTransactionId() {
        Payment payment = requested();
        payment.approve("PG-123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG-123");
    }

    @Test
    @DisplayName("PG 생략 자동 승인은 거래 ID 없이 APPROVED다")
    void approveWithoutGateway() {
        Payment payment = Payment.request(UUID.randomUUID(), Money.ZERO, null);
        payment.approveWithoutGateway();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isNull();
    }

    @Test
    @DisplayName("실패하면 FAILED이고 사유가 기록된다")
    void failSetsReason() {
        Payment payment = requested();
        payment.fail(FailureReason.INSUFFICIENT_BALANCE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("요청 상태가 아니면 다시 승인할 수 없다")
    void cannotApproveWhenNotRequested() {
        Payment payment = requested();
        payment.approve("PG-123");
        assertThatThrownBy(() -> payment.approve("PG-456")).isInstanceOf(PaymentStatusException.class);
    }

    @Test
    @DisplayName("승인 결제를 취소한다")
    void cancelApprovedPayment() {
        Payment payment = requested();
        payment.approve("PG-123");
        payment.cancel("CANCEL-123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getPgCancelTransactionId()).isEqualTo("CANCEL-123");
    }

    @Test
    @DisplayName("승인 상태가 아니면 취소할 수 없다")
    void cannotCancelWhenNotApproved() {
        assertThatThrownBy(() -> requested().cancel("CANCEL-123")).isInstanceOf(PaymentStatusException.class);
    }
}
