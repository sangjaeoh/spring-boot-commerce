package com.commerce.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.utility.DockerImageName;

class RedisLoginRateLimitStoreTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

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
    @DisplayName("같은 키의 연속 증가는 창 안에서 누적 카운트를 돌려준다")
    void countsAccumulateWithinWindow() {
        RedisLoginRateLimitStore store = new RedisLoginRateLimitStore(TEMPLATE);

        assertThat(store.incrementAndCount("accumulate", WINDOW)).isEqualTo(1L);
        assertThat(store.incrementAndCount("accumulate", WINDOW)).isEqualTo(2L);
        assertThat(store.incrementAndCount("accumulate", WINDOW)).isEqualTo(3L);
    }

    @Test
    @DisplayName("창(TTL)이 경과하면 카운터가 만료돼 다시 1부터 센다")
    void counterResetsAfterWindowExpires() {
        RedisLoginRateLimitStore store = new RedisLoginRateLimitStore(TEMPLATE);

        assertThat(store.incrementAndCount("reset", Duration.ofMillis(500))).isEqualTo(1L);
        assertThat(store.incrementAndCount("reset", Duration.ofMillis(500))).isEqualTo(2L);

        await().atMost(Duration.ofSeconds(3))
                .until(() -> store.incrementAndCount("reset", Duration.ofMillis(500)) == 1L);
    }

    @Test
    @DisplayName("같은 Redis를 쓰는 새 스토어 인스턴스(재시작)에서도 창 이내 카운트가 합산 유지된다")
    void countOutlivesStoreInstance() {
        RedisLoginRateLimitStore before = new RedisLoginRateLimitStore(TEMPLATE);
        assertThat(before.incrementAndCount("restart", WINDOW)).isEqualTo(1L);

        RedisLoginRateLimitStore after = new RedisLoginRateLimitStore(TEMPLATE);
        assertThat(after.incrementAndCount("restart", WINDOW)).isEqualTo(2L);
    }

    @Test
    @DisplayName("동시 증가는 정확히 시도 수만큼 카운트한다")
    void concurrentIncrementsCountExactly() throws InterruptedException {
        RedisLoginRateLimitStore store = new RedisLoginRateLimitStore(TEMPLATE);
        int threadCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.incrementAndCount("race", WINDOW);
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(store.incrementAndCount("race", WINDOW)).isEqualTo(threadCount + 1L);
    }
}
