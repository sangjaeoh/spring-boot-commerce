package com.commerce.infra.redis;

import com.commerce.web.ratelimit.LoginRateLimitStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** 레이트리밋 카운터를 Redis에 보관하는 {@link LoginRateLimitStore} 구현이다. */
@Component
final class RedisLoginRateLimitStore implements LoginRateLimitStore {

    private static final String KEY_PREFIX = "login-rate:";

    // 증가와 만료 설정을 한 스크립트로 묶는다 — 둘 사이의 크래시는 TTL 없는 키(영구 잔존·영구 차단)를 남긴다.
    private static final RedisScript<Long> INCREMENT_IN_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisLoginRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long incrementAndCount(String key, Duration window) {
        Long count =
                redisTemplate.execute(INCREMENT_IN_WINDOW, List.of(KEY_PREFIX + key), Long.toString(window.toMillis()));
        if (count == null) {
            // EVAL 정수 응답은 Long으로 변환된다 — null이면 도달할 수 없는 서버 버그다.
            throw new IllegalStateException("Redis 레이트리밋 스크립트가 값을 반환하지 않았다");
        }
        return count;
    }
}
