package com.commerce.common.cache;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
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

        // Lettuce 커넥션 팩토리는 리액티브도 지원하므로 기본값은 put·evict·clear를 비동기로 흘려보낸다(즉시 반영을
        // 보장하지 않음). evict 직후 조회가 갱신값을 즉시 봐야 하므로 즉시 쓰기(immediateWrites)로 강제한다.
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory, config -> config.immediateWrites());

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
                .withInitialCacheConfigurations(configurations)
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }
}
