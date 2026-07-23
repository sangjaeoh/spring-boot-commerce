package com.commerce.common.web.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 필터 테스트용 단일 인스턴스 인메모리 {@link LoginRateLimitStore} 구현이다. 실행 앱은 Redis 구현
 * (infra-redis)을 쓴다.
 *
 * <p>주입한 밀리초 소스로 창 경과를 결정론적으로 재현한다.
 */
final class InMemoryLoginRateLimitStore implements LoginRateLimitStore {

    private record Window(long endMillis, long count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier millisSource;

    InMemoryLoginRateLimitStore(LongSupplier millisSource) {
        this.millisSource = millisSource;
    }

    @Override
    public long incrementAndCount(String key, Duration window) {
        long now = millisSource.getAsLong();
        Window updated = windows.compute(key, (ignoredKey, existing) -> {
            if (existing == null || now >= existing.endMillis()) {
                return new Window(now + window.toMillis(), 1L);
            }
            return new Window(existing.endMillis(), existing.count() + 1L);
        });
        return updated.count();
    }
}
