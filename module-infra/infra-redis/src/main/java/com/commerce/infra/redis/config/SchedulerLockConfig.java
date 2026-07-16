package com.commerce.infra.redis.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 스케줄 잡 분산 락(ShedLock)의 Redis {@link LockProvider}를 배선한다.
 *
 * <p>잡 이름별 Redis 키를 {@code SET NX}로 선점하고 만료는 lockAtMostFor의 TTL이 처리한다. 락이 이미
 * 선점돼 있으면 획득이 빈 결과로 실패하고, Redis에 접근할 수 없으면 예외를 전파한다. Redis 채택 근거는
 * REQUIREMENTS.md 제약·전제가 소유한다.
 */
@Configuration
class SchedulerLockConfig {

    @Bean
    LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}
