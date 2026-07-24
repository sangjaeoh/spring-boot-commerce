package com.commerce.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** evict 실패를 흡수하는 트랜잭션 어웨어 {@link CacheManager}다. 저장소 무관 — 임의 델리게이트를 감쌀 수 있다. */
public final class EvictSafeCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final MeterRegistry meterRegistry;

    public EvictSafeCacheManager(CacheManager delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public @Nullable Cache getCache(String name) {
        Cache target = delegate.getCache(name);
        return target == null ? null : new EvictSafeCache(target, meterRegistry);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
