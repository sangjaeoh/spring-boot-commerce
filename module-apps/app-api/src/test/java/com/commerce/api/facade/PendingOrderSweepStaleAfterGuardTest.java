package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.service.PaymentReader;
import com.commerce.stock.service.StockModifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 리컨실 유예 관계(order 유예 ≥ payment 유예)의 기동 시점 검증을 확인하는 테스트다 — 역전 설정이면 스윕 파사드
 * 생성(빈 배선)이 실패해 애플리케이션이 뜨지 않는다.
 */
class PendingOrderSweepStaleAfterGuardTest {

    @Test
    @DisplayName("order 유예가 payment 유예보다 짧으면 생성 시점에 거부된다")
    void rejectsOrderStaleAfterShorterThanPayment() {
        assertThatThrownBy(() -> construct(Duration.ofMinutes(5), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("order 유예가 payment 유예와 같거나 길면 생성된다")
    void acceptsOrderStaleAfterAtLeastPayment() {
        assertThatCode(() -> construct(Duration.ofMinutes(10), Duration.ofMinutes(10)))
                .doesNotThrowAnyException();
        assertThatCode(() -> construct(Duration.ofMinutes(15), Duration.ofMinutes(10)))
                .doesNotThrowAnyException();
    }

    private PendingOrderSweepFacade construct(Duration orderStaleAfter, Duration paymentStaleAfter) {
        return new PendingOrderSweepFacade(
                mock(OrderReader.class),
                mock(PaymentReader.class),
                mock(OrderModifier.class),
                mock(StockModifier.class),
                mock(IssuedCouponModifier.class),
                mock(PaymentConfirmationFacade.class),
                orderStaleAfter,
                paymentStaleAfter,
                Clock.systemUTC(),
                new SimpleMeterRegistry());
    }
}
