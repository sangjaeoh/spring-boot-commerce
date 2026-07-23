package com.commerce.domain.member.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.utility.DockerImageName;

class RedisOneTimeTokenStoreTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String NAMESPACE = "password-reset";

    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:8.2-alpine"));
    private static final StringRedisTemplate TEMPLATE = createTemplate();

    /** Redis 컨테이너를 시작하고 연결된 템플릿을 만든다. 컨테이너·커넥션은 JVM 수명 동안 공유하고 종료 시 Testcontainers가 정리한다. */
    private static StringRedisTemplate createTemplate() {
        REDIS.start();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    private final RedisOneTimeTokenStore store = new RedisOneTimeTokenStore(TEMPLATE);

    @Test
    @DisplayName("보관한 토큰의 consume은 주체를 반환하고, 재consume은 빈 결과다")
    void consumeReturnsSubjectOnceOnly() {
        store.save(NAMESPACE, "saved-token", "member-1", TTL);

        assertThat(store.consume(NAMESPACE, "saved-token")).contains("member-1");
        assertThat(store.consume(NAMESPACE, "saved-token")).isEmpty();
    }

    @Test
    @DisplayName("TTL이 지난 토큰의 consume은 빈 결과다")
    void consumeReturnsEmptyAfterTtl() {
        store.save(NAMESPACE, "expiring-token", "member-1", Duration.ofMillis(300));

        await().atMost(Duration.ofSeconds(3))
                .until(() -> store.consume(NAMESPACE, "expiring-token").isEmpty());
    }

    @Test
    @DisplayName("네임스페이스가 다르면 consume은 빈 결과이고 원 네임스페이스의 토큰은 남는다")
    void consumeIsolatesNamespaces() {
        store.save(NAMESPACE, "scoped-token", "member-1", TTL);

        assertThat(store.consume("email-verification", "scoped-token")).isEmpty();
        assertThat(store.consume(NAMESPACE, "scoped-token")).contains("member-1");
    }

    @Test
    @DisplayName("보관한 적 없는 토큰의 consume은 빈 결과다")
    void consumeReturnsEmptyForUnknownToken() {
        assertThat(store.consume(NAMESPACE, "unknown-token")).isEmpty();
    }
}
