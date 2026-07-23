package com.commerce.app.admin;

import com.redis.testcontainers.RedisContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * app-admin 통합 테스트가 공유하는 단일 Redis 컨테이너 홀더다.
 *
 * <p>JVM 수명 동안 컨테이너 하나만 띄워 멱등 키 저장소로 쓴다. 컨테이너는 명시적으로 멈추지
 * 않고 JVM 종료 시 Testcontainers가 정리한다.
 */
public final class SharedRedisContainer {

    public static final RedisContainer INSTANCE = new RedisContainer(DockerImageName.parse("redis:8.2-alpine"));

    static {
        INSTANCE.start();
    }

    private SharedRedisContainer() {}
}
