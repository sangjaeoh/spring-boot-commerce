package com.commerce.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.utility.DockerImageName;

class RedisIdempotencyStoreTest {

    private static final Duration IN_FLIGHT = Duration.ofMinutes(5);
    private static final Duration WINDOW = Duration.ofSeconds(10);

    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:8.2-alpine"));
    private static final StringRedisTemplate TEMPLATE = createTemplate();

    // 컨테이너·커넥션은 JVM 수명 동안 공유하고 종료 시 Testcontainers가 정리한다.
    private static StringRedisTemplate createTemplate() {
        REDIS.start();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    @Test
    @DisplayName("최초 tryBegin은 성공하고, 진행 중 같은 키의 재선점은 실패한다")
    void inFlightGuardRejectsSecondBegin() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, WINDOW);

        assertThat(store.tryBegin("begin")).isTrue();
        assertThat(store.tryBegin("begin")).isFalse();
    }

    @Test
    @DisplayName("완료 후 창(window) 이내 재요청은 거부되고, 창 경과 후 다시 허용된다")
    void completedKeyStaysLockedForWindowThenReopens() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, Duration.ofMillis(500));

        assertThat(store.tryBegin("window")).isTrue();
        store.complete("window");
        assertThat(store.tryBegin("window")).isFalse();

        await().atMost(Duration.ofSeconds(3)).until(() -> store.tryBegin("window"));
    }

    @Test
    @DisplayName("완료 전 in-flight 락은 dedup 창보다 오래 유지돼, 창 경과 후 도착한 타임아웃 재시도도 거부한다")
    void inFlightLockOutlivesWindowUntilComplete() throws InterruptedException {
        RedisIdempotencyStore store = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, Duration.ofMillis(200));

        assertThat(store.tryBegin("in-flight")).isTrue();
        Thread.sleep(400);
        assertThat(store.tryBegin("in-flight")).isFalse();
    }

    @Test
    @DisplayName("완료 없이 in-flight TTL이 지나면(크래시 등) 키가 회수돼 다시 선점할 수 있다")
    void unfinishedKeyReopensAfterInFlightTtl() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(TEMPLATE, Duration.ofMillis(500), WINDOW);

        assertThat(store.tryBegin("crash")).isTrue();
        await().atMost(Duration.ofSeconds(3)).until(() -> store.tryBegin("crash"));
    }

    @Test
    @DisplayName("같은 Redis를 쓰는 새 스토어 인스턴스(재시작)에서도 창 이내 같은 키는 거부된다")
    void keyOutlivesStoreInstance() {
        RedisIdempotencyStore before = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, WINDOW);
        assertThat(before.tryBegin("restart")).isTrue();
        before.complete("restart");

        RedisIdempotencyStore after = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, WINDOW);
        assertThat(after.tryBegin("restart")).isFalse();
    }

    @Test
    @DisplayName("동시 tryBegin은 정확히 하나만 선점한다")
    void concurrentBeginYieldsExactlyOneWinner() throws InterruptedException {
        RedisIdempotencyStore store = new RedisIdempotencyStore(TEMPLATE, IN_FLIGHT, WINDOW);
        int threadCount = 32;
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
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get()).isEqualTo(1);
    }
}
