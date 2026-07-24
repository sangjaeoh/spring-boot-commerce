package com.commerce.app.api.config;

import com.commerce.common.cache.CacheRegistration;
import com.commerce.common.cache.EvictSafeCacheManager;
import com.commerce.common.cache.RedisCacheManagerFactory;
import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryCacheNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.type.TypeFactory;

/**
 * 캐시 전용 Redis 커넥션·CacheManager를 구성한다.
 *
 * <p>기존 공유 Redis 커넥션(멱등 키·레이트리밋·리프레시 토큰·스케줄 락)과 분리된 전용 커넥션을 쓴다 —
 * command timeout(300ms)을 캐시 전용으로 짧게 잡아 장애 시 빠르게 미스로 강등하기 위함이다.
 */
@Configuration
@EnableCaching
class CacheConfig implements CachingConfigurer {

    private final String redisHost;
    private final int redisPort;
    private final MeterRegistry meterRegistry;

    CacheConfig(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort,
            MeterRegistry meterRegistry) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Bean
    public CacheManager cacheManager() {
        // 캐시 전용 커넥션 팩토리를 로컬 변수로 생성한다(빈이 아님).
        // 이렇게 하면 Boot의 공유 RedisConnectionFactory 자동구성이 억제되지 않는다.
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .build();
        LettuceConnectionFactory cacheConnectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisHost, redisPort), clientConfiguration);
        try {
            cacheConnectionFactory.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize cache connection factory", e);
        }

        List<CacheRegistration> registrations = List.of(new CacheRegistration(
                CategoryCacheNames.CATEGORY,
                Duration.ofMinutes(10),
                TypeFactory.createDefaultInstance().constructCollectionType(List.class, CategoryInfo.class)));
        org.springframework.data.redis.cache.RedisCacheManager redisCacheManager =
                RedisCacheManagerFactory.build(cacheConnectionFactory, registrations);
        try {
            redisCacheManager.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RedisCacheManager", e);
        }
        return new EvictSafeCacheManager(redisCacheManager, meterRegistry);
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
