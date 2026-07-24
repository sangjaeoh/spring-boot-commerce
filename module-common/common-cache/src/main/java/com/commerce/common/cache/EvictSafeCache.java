package com.commerce.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * evict·clear를 커밋 후로 지연시키고 실패를 로그·메트릭으로 흡수하는 {@link Cache} 데코레이터다.
 *
 * <p>Spring 내장 {@code TransactionAwareCacheDecorator}는 지연 실행 중 예외를 흡수하지 않고 커밋 경로로
 * 전파하므로 쓰지 않는다.
 */
final class EvictSafeCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(EvictSafeCache.class);

    private final Cache delegate;
    private final Counter evictErrorCounter;

    EvictSafeCache(Cache delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.evictErrorCounter = Counter.builder("cache.evict.errors")
                .tag("cache.name", delegate.getName())
                .register(meterRegistry);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public @Nullable ValueWrapper get(Object key) {
        return delegate.get(key);
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
        return delegate.get(key, valueLoader);
    }

    @Override
    public @Nullable CompletableFuture<?> retrieve(Object key) {
        return delegate.retrieve(key);
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        return delegate.retrieve(key, valueLoader);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        delegate.put(key, value);
    }

    @Override
    public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        runAfterCommitOrNow(() -> delegate.evict(key));
    }

    @Override
    public boolean evictIfPresent(Object key) {
        return delegate.evictIfPresent(key);
    }

    @Override
    public void clear() {
        runAfterCommitOrNow(delegate::clear);
    }

    @Override
    public boolean invalidate() {
        return delegate.invalidate();
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("evict 실패 — cache={}", delegate.getName(), ex);
                    evictErrorCounter.increment();
                }
            }
        });
    }
}
