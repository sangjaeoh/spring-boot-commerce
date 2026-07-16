package com.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.commerce.core.money.Money;
import com.commerce.payment.entity.Payment;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.exception.DuplicatePaymentException;
import com.commerce.payment.exception.PaymentStatusException;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentProcessor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

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
        assertThat(gateway.cancelCalled).isFalse();
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
    @DisplayName("이미 승인된 결제 재요청은 PG를 청구하지 않고 상태 가드로 거부한다(원 승인 불변)")
    void rejectsReapprovalWithoutCharging() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        em.flush();
        PaymentInfo first = paymentProcessor.approve(paymentId);
        em.flush();
        gateway.reset();

        assertThatThrownBy(() -> paymentProcessor.approve(paymentId)).isInstanceOf(PaymentStatusException.class);

        assertThat(gateway.cancelCalled).isFalse();
        Payment reloaded = reload(paymentId);
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(reloaded.getPgTransactionId()).isEqualTo(first.pgTransactionId());
    }

    @Test
    @DisplayName("청구 성공 후 결과 영속이 실패하면 그 고아 청구를 환불한다 — PG 호출이 트랜잭션 밖이라 보상이 가능하다")
    void refundsOrphanedChargeWhenPersistFails() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        em.flush();
        Payment requested = paymentRepository.findById(paymentId).orElseThrow();
        // 트랜잭션 밖 선조회는 통과시키고, 결과 영속 안의 재조회에서 인프라 실패를 주입한다.
        doReturn(Optional.of(requested))
                .doThrow(new IllegalStateException("persist boom"))
                .when(paymentRepository)
                .findById(paymentId);
        gateway.reset();

        assertThatThrownBy(() -> paymentProcessor.approve(paymentId)).isInstanceOf(IllegalStateException.class);

        assertThat(gateway.cancelCalled).isTrue();
    }

    @Test
    @DisplayName("환불 성공 후 영속이 실패해도 재시도는 이중 환불하지 않는다 — 같은 멱등 키로 PG가 환불을 한 번만 수행한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelDoesNotDoubleRefundWhenPersistFailsThenRetries() {
        // NOT_SUPPORTED로 @DataJpaTest의 롤백 트랜잭션을 걷어내, 두 취소 시도가 각자 실제 물리 트랜잭션으로
        // 커밋한다(운영과 동일: 파사드는 트랜잭션을 열지 않는다). 상태는 em이 아니라 리포지토리로 되읽는다.
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        paymentProcessor.approve(paymentId);
        gateway.reset();
        Payment approved = paymentRepository.findById(paymentId).orElseThrow();

        // 1차: 트랜잭션 밖 사전조회는 통과시키고, 결과 영속 안의 재조회에서 인프라 실패를 주입한다.
        doReturn(Optional.of(approved))
                .doThrow(new IllegalStateException("persist boom"))
                .when(paymentRepository)
                .findById(paymentId);
        assertThatThrownBy(() -> paymentProcessor.cancel(paymentId)).isInstanceOf(IllegalStateException.class);
        assertThat(gateway.refundCount).isEqualTo(1); // 환불은 실제로 한 번 일어났다

        // 영속이 롤백돼 결제 행은 APPROVED로 남고 창이 열려 있다. 스텁을 걷어내고 실제 리포지토리로 되읽는다.
        Mockito.reset(paymentRepository);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.APPROVED);

        // 재시도: 같은 멱등 키로 PG.cancel이 다시 불려도 환불은 늘지 않고 취소가 확정된다.
        // (스텁이 키로 멱등이라 성립한다 — 실 벤더의 멱등을 증명하지는 못한다.)
        paymentProcessor.cancel(paymentId);

        assertThat(gateway.refundCount).isEqualTo(1);
        Payment cancelled = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getPgCancelTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("승인·취소 전이가 반영되고 낙관락 버전이 증가한다")
    void transitionsIncrementVersion() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        assertThat(reload(paymentId).getVersion()).isZero();

        paymentProcessor.approve(paymentId);
        assertThat(reload(paymentId).getVersion()).isEqualTo(1L);

        paymentProcessor.cancel(paymentId);
        assertThat(reload(paymentId).getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("고아 승인 환불 후 한 커밋 기록이 실패해도 재시도는 이중 환불하지 않는다 — 같은 멱등 키로 PG가 한 번만 환불한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void confirmOrphanedApprovalDoesNotDoubleRefundWhenPersistFailsThenRetries() {
        UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(10000L), PaymentMethod.CARD);
        // 1차: PG 환불(트랜잭션 밖)은 실제로 일어나고, 승인·취소 한 커밋 기록의 재조회에서 인프라 실패를 주입한다.
        doThrow(new IllegalStateException("persist boom"))
                .when(paymentRepository)
                .findById(paymentId);

        assertThatThrownBy(() -> paymentProcessor.confirmOrphanedApproval(paymentId, "PG-ORPHAN"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gateway.refundCount).isEqualTo(1);

        // 기록이 롤백돼 결제는 REQUESTED로 남는다 — 리컨실 대상에서 이탈하지 않는다(자기복구 창 유지).
        Mockito.reset(paymentRepository);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUESTED);

        // 재시도: 같은 멱등 키(CANCEL:pgTransactionId)라 PG 환불은 늘지 않고 승인·취소 기록이 완결된다.
        paymentProcessor.confirmOrphanedApproval(paymentId, "PG-ORPHAN");

        assertThat(gateway.refundCount).isEqualTo(1);
        Payment settled = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(settled.getPgTransactionId()).isEqualTo("PG-ORPHAN");
        assertThat(settled.getPgCancelTransactionId()).isNotNull();
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
