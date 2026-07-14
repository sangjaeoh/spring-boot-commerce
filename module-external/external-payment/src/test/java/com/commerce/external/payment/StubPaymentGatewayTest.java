package com.commerce.external.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentApproval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StubPaymentGatewayTest {

    private final StubPaymentGateway gateway = new StubPaymentGateway();

    @Test
    @DisplayName("승인은 항상 성공하고 거래 ID를 발급한다")
    void approveAlwaysSucceedsWithTransactionId() {
        PaymentApproval result = gateway.approve(Money.of(10_000), PaymentMethod.CARD);

        assertThat(result.approved()).isTrue();
        assertThat(result.pgTransactionId()).isNotNull();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("승인마다 거래 ID는 유일하다")
    void eachApprovalHasUniqueTransactionId() {
        PaymentApproval first = gateway.approve(Money.of(1_000), PaymentMethod.EASY_PAY);
        PaymentApproval second = gateway.approve(Money.of(1_000), PaymentMethod.EASY_PAY);

        assertThat(first.pgTransactionId()).isNotNull().isNotEqualTo(second.pgTransactionId());
    }

    @Test
    @DisplayName("취소는 취소 거래 ID를 반환한다")
    void cancelReturnsCancelTransactionId() {
        String cancelTransactionId = gateway.cancel("STUB-APPROVE-abc");

        assertThat(cancelTransactionId).isNotBlank();
    }
}
