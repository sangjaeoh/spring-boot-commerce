package com.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.exception.DuplicatePaymentException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentProcessor;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * payment 도메인의 영속 이음새와 PG 조율을 실 PostgreSQL로 검증한다.
 *
 * <p>{@code ddl-auto=validate} 정합, 주문당 결제 유니크, 승인·거절·PG 생략·환불·환불 생략 분기를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/payment",
            "spring.flyway.schemas=payment",
            "spring.flyway.default-schema=payment"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PaymentAppender.class, PaymentProcessor.class, StubPaymentGateway.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PaymentPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final PaymentAppender paymentAppender;
    private final PaymentProcessor paymentProcessor;
    private final StubPaymentGateway gateway;
    private final TestEntityManager em;

    PaymentPersistenceTest(
            PaymentAppender paymentAppender,
            PaymentProcessor paymentProcessor,
            StubPaymentGateway gateway,
            TestEntityManager em) {
        this.paymentAppender = paymentAppender;
        this.paymentProcessor = paymentProcessor;
        this.gateway = gateway;
        this.em = em;
    }

    @BeforeEach
    void resetGateway() {
        gateway.reset();
    }

    private Payment reload(UUID paymentId) {
        em.flush();
        em.clear();
        return Objects.requireNonNull(em.find(Payment.class, paymentId));
    }

    @Test
    @DisplayName("PG 조율 승인이 거래 ID와 함께 반영된다 — validate 스키마 정합")
    void approveWithGateway() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        em.flush();

        PaymentInfo result = paymentProcessor.approve(paymentId);

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.pgTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("PG 거절은 FAILED로 사유와 함께 반영되고 거래 ID가 없다")
    void declineFailsPayment() {
        gateway.decline = true;
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        em.flush();

        PaymentInfo result = paymentProcessor.approve(paymentId);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).isNotNull();
        assertThat(result.pgTransactionId()).isNull();
    }

    @Test
    @DisplayName("금액 0 결제는 PG를 생략하고 자동 승인된다")
    void approveZeroAmountWithoutGateway() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.ZERO, null);
        em.flush();

        PaymentInfo result = paymentProcessor.approve(paymentId);

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.pgTransactionId()).isNull();
    }

    @Test
    @DisplayName("PG 승인 결제 취소는 환불을 호출하고 취소 거래 ID를 남긴다")
    void cancelWithRefund() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        em.flush();
        paymentProcessor.approve(paymentId);
        em.flush();

        paymentProcessor.cancel(paymentId);

        assertThat(gateway.cancelCalled).isTrue();
        Payment cancelled = reload(paymentId);
        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getPgCancelTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("PG 미호출 승인(금액 0) 취소는 환불 호출을 생략한다")
    void cancelSkipsRefundForZeroAmount() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.ZERO, null);
        em.flush();
        paymentProcessor.approve(paymentId);
        em.flush();

        paymentProcessor.cancel(paymentId);

        assertThat(gateway.cancelCalled).isFalse();
        assertThat(reload(paymentId).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문당 결제는 하나뿐이라 중복 요청은 거부된다")
    void duplicatePaymentRejected() {
        UUID orderId = UUID.randomUUID();
        paymentAppender.request(orderId, Money.of(10000L), PaymentMethod.CARD);
        em.flush();

        assertThatThrownBy(() -> paymentAppender.request(orderId, Money.of(5000L), PaymentMethod.CARD))
                .isInstanceOf(DuplicatePaymentException.class);
    }
}
