package com.commerce.common.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    private static final long IN_FLIGHT_MILLIS = 300_000L;
    private static final long WINDOW_MILLIS = 10_000L;

    @Test
    @DisplayName("최초 tryBegin은 성공하고, 진행 중 같은 키의 재선점은 실패한다")
    void inFlightGuardRejectsConcurrentBegin() {
        long[] now = {1_000L};
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(() -> now[0], IN_FLIGHT_MILLIS, WINDOW_MILLIS);

        assertThat(store.tryBegin("k")).isTrue();
        assertThat(store.tryBegin("k")).isFalse();
    }

    @Test
    @DisplayName("완료 후 창(window) 이내 재요청은 거부되고, 창 경과 후 다시 허용된다")
    void completedKeyStaysLockedForWindowThenReopens() {
        long[] now = {1_000L};
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(() -> now[0], IN_FLIGHT_MILLIS, WINDOW_MILLIS);

        assertThat(store.tryBegin("k")).isTrue();
        store.complete("k");
        assertThat(store.tryBegin("k")).isFalse();

        now[0] += WINDOW_MILLIS + 1;
        assertThat(store.tryBegin("k")).isTrue();
    }

    @Test
    @DisplayName("완료 전 in-flight 락은 dedup 창보다 오래 유지돼, 창 경과 후 도착한 타임아웃 재시도도 거부한다")
    void inFlightLockOutlivesWindowUntilComplete() {
        long[] now = {1_000L};
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(() -> now[0], IN_FLIGHT_MILLIS, WINDOW_MILLIS);

        // 원본 요청 시작(미완료)
        assertThat(store.tryBegin("k")).isTrue();
        // dedup 창은 지났으나 요청은 여전히 in-flight
        now[0] += WINDOW_MILLIS + 1;
        // 타임아웃 재시도가 새로 획득하지 못한다
        assertThat(store.tryBegin("k")).isFalse();
    }

    @Test
    @DisplayName("동시 tryBegin은 정확히 하나만 선점한다")
    void concurrentBeginYieldsExactlyOneWinner() throws InterruptedException {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        int threadCount = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (store.tryBegin("race")) {
                    winners.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get()).isEqualTo(1);
    }
}
