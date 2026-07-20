package com.commerce.infra.redis.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** 스케줄 스윕 분산 락(ShedLock)을 배선하는 설정이다. */
@Configuration
class SchedulerLockConfig {

    /** 스윕 이름별 락을 Redis에 보관하는 {@link LockProvider}를 공급한다. */
    @Bean
    LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}
