package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.commerce.payment.service.PaymentReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 스케줄 스윕의 ShedLock 분산 락 배선을 검증하는 테스트다 — 락 상태가 Redis에 있으므로 한 컨텍스트의 동시 두
 * 호출이 다중 인스턴스의 동시 실행과 같은 지점에서 경합한다. 한쪽만 스윕을 수행하고 다른 쪽은 정상
 * 건너뛰어야 한다.
 *
 * <p>다른 캐시된 컨텍스트의 백그라운드 스케줄 실행과 락을 공유하지 않도록 전용 Redis 데이터베이스를
 * 쓰고, 이 컨텍스트 자신의 스케줄 실행은 두 스윕 모두 fixed-delay를 늘려 기동 직후 1회로 고정한다. 그
 * 초기 실행과의 경합은 같은 락을 테스트가 선점(펜스)한 채 스텁을 설치하는 것으로 차단한다 — 펜스 보유
 * 중에 도착한 초기 실행은 건너뛰고, 펜스 이전에 끝난 초기 실행의 호출 기록은 {@code clearInvocations}가
 * 지운다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SchedulerLockTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private PaymentReader paymentReader;

    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final LockProvider lockProvider;

    SchedulerLockTest(PaymentConfirmationFacade paymentConfirmationFacade, LockProvider lockProvider) {
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.lockProvider = lockProvider;
    }

    @DynamicPropertySource
    static void schedulerLockProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.database", () -> "15");
        registry.add("payment.reconciliation.fixed-delay", () -> "PT1H");
        registry.add("order.reconciliation.fixed-delay", () -> "PT1H");
    }

    @Test
    @DisplayName("동시 두 리컨실 실행 중 한쪽만 락을 획득해 스윕을 수행하고 다른 쪽은 건너뛴다")
    void onlyOneOfConcurrentSweepsAcquiresLockAndRuns() throws Exception {
        CountDownLatch sweepEntered = new CountDownLatch(1);
        CountDownLatch sweepRelease = new CountDownLatch(1);

        // 초기 스케줄 실행이 진행 중이면 끝나기를 기다려 락을 선점하고, 스텁 설치를 마칠 때까지 쥔다.
        SimpleLock fence = acquireLockFence();
        clearInvocations(paymentReader);
        doAnswer(invocation -> {
                    sweepEntered.countDown();
                    if (!sweepRelease.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("테스트가 스윕을 해제하지 않았다");
                    }
                    return invocation.callRealMethod();
                })
                .when(paymentReader)
                .findRequestedBefore(any());
        fence.unlock();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(paymentConfirmationFacade::reconcileStaleRequested);
            assertThat(sweepEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // 락 보유 중의 두 번째 실행 — 스윕에 들어가지 않고 즉시 정상 반환해야 한다.
            Future<?> skipped = executor.submit(paymentConfirmationFacade::reconcileStaleRequested);
            skipped.get(5, TimeUnit.SECONDS);

            // 락이 아직 잡혀 있는 시점의 검증 — 이후에 끼어들 실행이 스윕에 진입할 수 없는 동안 센다.
            verify(paymentReader, times(1)).findRequestedBefore(any());

            sweepRelease.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            sweepRelease.countDown();
            executor.shutdownNow();
        }
    }

    /** 스윕과 같은 락을 선점해 반환한다 — 이 락을 쥔 동안 스케줄 실행은 스윕에 진입하지 못한다. */
    private SimpleLock acquireLockFence() {
        AtomicReference<SimpleLock> fence = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            lockProvider
                    .lock(new LockConfiguration(
                            Instant.now(), PaymentConfirmationFacade.LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO))
                    .ifPresent(fence::set);
            return fence.get() != null;
        });
        return Objects.requireNonNull(fence.get());
    }
}
