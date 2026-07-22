package com.commerce.member.adapter.persistence;

import com.commerce.member.application.required.OneTimeTokenStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 1회용 토큰을 Redis에 보관하는 {@link OneTimeTokenStore} 구현이다. */
@Component
final class RedisOneTimeTokenStore implements OneTimeTokenStore {

    private static final String KEY_PREFIX = "one-time-token:";

    private final StringRedisTemplate redisTemplate;

    RedisOneTimeTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String namespace, String token, String subject, Duration ttl) {
        redisTemplate.opsForValue().set(key(namespace, token), subject, ttl);
    }

    @Override
    public Optional<String> consume(String namespace, String token) {
        // 조회와 삭제를 GETDEL 한 번으로 묶어 동시 소비 경합에서도 1회만 성공한다.
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key(namespace, token)));
    }

    private static String key(String namespace, String token) {
        return KEY_PREFIX + namespace + ":" + token;
    }
}
