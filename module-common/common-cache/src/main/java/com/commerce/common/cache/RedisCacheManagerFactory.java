package com.commerce.common.cache;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

/** 이름별 TTL·값 타입 등록 목록으로 RedisCacheManager를 조립한다. 등록되지 않은 이름은 생성하지 않는다. */
public final class RedisCacheManagerFactory {

    private RedisCacheManagerFactory() {}

    public static RedisCacheManager build(
            RedisConnectionFactory connectionFactory, List<CacheRegistration> registrations) {
        JsonMapper mapper = JsonMapper.builder().build();

        Map<String, RedisCacheConfiguration> configurations = registrations.stream()
                .collect(Collectors.toMap(
                        CacheRegistration::name,
                        registration -> RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(registration.ttl())
                                .disableCachingNullValues()
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                        new JacksonJsonRedisSerializer<>(mapper, registration.valueType())))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
                .withInitialCacheConfigurations(configurations)
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }
}
