package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.type.TypeFactory;

class RedisCacheManagerFactoryTest {

    @Test
    void registersOnlyDeclaredCacheNamesWithConfiguredTtl() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        var valueType = TypeFactory.createDefaultInstance().constructType(String.class);
        var registrations = List.of(new CacheRegistration("product:category:v1", Duration.ofMinutes(10), valueType));

        var cacheManager = RedisCacheManagerFactory.build(connectionFactory, registrations);
        cacheManager.afterPropertiesSet();

        assertThat(cacheManager.getCacheConfigurations()).containsOnlyKeys("product:category:v1");
        var cacheConfig =
                Objects.requireNonNull(cacheManager.getCacheConfigurations().get("product:category:v1"));
        assertThat(cacheConfig.getTtlFunction()).isNotNull();
        assertThat(cacheManager.isAllowRuntimeCacheCreation()).isFalse();
    }
}
