package com.commerce.infra.redis;

import com.commerce.auth.token.RefreshTokenStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 리프레시 토큰을 Redis에 보관하는 {@link RefreshTokenStore} 구현이다. */
@Component
final class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate redisTemplate;

    RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String token, String subject, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, subject, ttl);
    }

    @Override
    public Optional<String> findSubject(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + token));
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
