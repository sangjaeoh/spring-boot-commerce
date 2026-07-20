package com.commerce.infra.redis;

import com.commerce.web.idempotency.IdempotencyStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 멱등 키를 Redis에 보관하는 {@link IdempotencyStore} 구현이다. */
@Component
final class RedisIdempotencyStore implements IdempotencyStore {

    // in-flight 락 수명. 최대 요청 처리 시간(동기 결제 등)·클라이언트/프록시 타임아웃 재시도 지연을 넘도록
    // 넉넉히 잡는다 — 그래야 타임아웃 재시도가 원본 처리 중에 락 자기만료로 새로 획득하지 못한다. 완료 없이
    // 이 시간이 지나면(크래시 등) TTL 만료로 키를 회수한다.
    private static final Duration DEFAULT_IN_FLIGHT = Duration.ofMinutes(5);
    // 완료 후 재요청을 거부하는 dedup 창. 더블서밋(즉시 재클릭)·즉시 재시도를 흡수한다.
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(10);
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
        redisTemplate.opsForValue().set(KEY_PREFIX + key, "", window);
    }
}
