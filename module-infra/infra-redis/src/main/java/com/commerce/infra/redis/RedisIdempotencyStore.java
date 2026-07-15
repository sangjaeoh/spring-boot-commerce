package com.commerce.infra.redis;

import com.commerce.web.idempotency.IdempotencyStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 멱등 키를 Redis에 보관하는 {@link IdempotencyStore} 구현이다.
 *
 * <p>{@code SET NX}(원자 선점)로 동시 중복 요청 중 하나만 키를 획득하고, 만료는 Redis TTL이 처리한다.
 * 키가 프로세스 밖에 보관되므로 재시작·다중 인스턴스에서도 중복 차단이 유지된다. Redis에 접근할 수 없으면
 * 예외를 전파한다(fail-closed) — 중복 차단 없이 요청을 통과시키지 않는다.
 */
@Component
final class RedisIdempotencyStore implements IdempotencyStore {

    // in-flight 락 수명. 최대 요청 처리 시간(동기 결제 등)·클라이언트/프록시 타임아웃 재시도 지연을 넘도록
    // 넉넉히 잡는다 — 그래야 타임아웃 재시도가 원본 처리 중에 락 자기만료로 새로 획득하지 못한다. 완료 없이
    // 이 시간이 지나면(크래시 등) TTL 만료로 키를 회수한다.
    private static final Duration DEFAULT_IN_FLIGHT = Duration.ofMinutes(5);
    // 완료 후 재요청을 거부하는 dedup 창. 더블서밋(즉시 재클릭)·즉시 재시도를 흡수한다.
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(10);
    // 공유 키스페이스에서 멱등 키를 다른 용도와 구분하는 접두사.
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final Duration inFlight;
    private final Duration window;

    @Autowired
    RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_IN_FLIGHT, DEFAULT_WINDOW);
    }

    RedisIdempotencyStore(StringRedisTemplate redisTemplate, Duration inFlight, Duration window) {
        this.redisTemplate = redisTemplate;
        this.inFlight = inFlight;
        this.window = window;
    }

    @Override
    public boolean tryBegin(String key) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "", inFlight));
    }

    @Override
    public void complete(String key) {
        // 완료 시 in-flight 락을 짧은 dedup 창으로 단축한다.
        redisTemplate.opsForValue().set(KEY_PREFIX + key, "", window);
    }
}
