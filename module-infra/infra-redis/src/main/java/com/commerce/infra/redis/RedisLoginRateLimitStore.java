package com.commerce.infra.redis;

import com.commerce.web.ratelimit.LoginRateLimitStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 레이트리밋 카운터를 Redis에 보관하는 {@link LoginRateLimitStore} 구현이다.
 *
 * <p>{@code INCR}로 창 카운터를 증가시키고 그 키의 첫 증가에서만 {@code PEXPIRE}로 창을 건다 — 두 명령을 한
 * Lua 스크립트로 원자 실행해, 증가와 만료 설정 사이의 크래시가 TTL 없는 키(영구 잔존·영구 차단)를 남기지 않게
 * 한다. 카운터가 프로세스 밖에 보관되므로 재시작·다중 인스턴스에서도 한도가 합산 유지된다. Redis에 접근할 수
 * 없으면 예외를 전파한다(fail-closed) — 멱등 저장소와 같은 정합성 우선 결정이다.
 */
@Component
final class RedisLoginRateLimitStore implements LoginRateLimitStore {

    // 공유 키스페이스에서 레이트리밋 키를 다른 용도와 구분하는 접두사.
    private static final String KEY_PREFIX = "login-rate:";

    // 카운터 증가 + 첫 증가 시 창 설정을 원자로 수행한다. 증가 후 값을 돌려준다.
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
