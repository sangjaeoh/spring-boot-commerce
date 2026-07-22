package com.commerce.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.utility.DockerImageName;

class RedisRefreshTokenStoreTest {

    private static final Duration TTL = Duration.ofMinutes(5);

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

    private final RedisRefreshTokenStore store = new RedisRefreshTokenStore(TEMPLATE);

    @Test
    @DisplayName("보관한 토큰의 findSubject는 저장한 주체를 반환한다")
    void findSubjectReturnsSavedSubject() {
        store.save("saved-token", "member-1", TTL);

        assertThat(store.findSubject("saved-token")).contains("member-1");
    }

    @Test
    @DisplayName("TTL이 지난 토큰의 findSubject는 빈 결과다")
    void findSubjectReturnsEmptyAfterTtl() {
        store.save("expiring-token", "member-1", Duration.ofMillis(300));

        await().atMost(Duration.ofSeconds(3))
                .until(() -> store.findSubject("expiring-token").isEmpty());
    }

    @Test
    @DisplayName("삭제한 토큰의 findSubject는 빈 결과이고, 없는 토큰 삭제는 아무 일도 하지 않는다")
    void deleteRemovesTokenAndIsIdempotent() {
        store.save("deleted-token", "member-1", TTL);

        store.delete("deleted-token");
        assertThat(store.findSubject("deleted-token")).isEmpty();

        store.delete("deleted-token");
        assertThat(store.findSubject("deleted-token")).isEmpty();
    }

    @Test
    @DisplayName("보관한 적 없는 토큰의 findSubject는 빈 결과다")
    void findSubjectReturnsEmptyForUnknownToken() {
        assertThat(store.findSubject("unknown-token")).isEmpty();
    }
}
