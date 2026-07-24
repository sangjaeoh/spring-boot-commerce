package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EvictSafeCacheManagerTest {

    private final ConcurrentMapCacheManager delegate = new ConcurrentMapCacheManager("categories");
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final EvictSafeCacheManager cacheManager = new EvictSafeCacheManager(delegate, meterRegistry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsImmediatelyWithoutActiveTransaction() {
        var cache = Objects.requireNonNull(cacheManager.getCache("categories"));
        cache.put("all", "stale");

        cache.evict("all");

        var delegateCache = Objects.requireNonNull(delegate.getCache("categories"));
        assertThat(delegateCache.get("all")).isNull();
    }

    @Test
    void deferEvictUntilAfterCommitWhenTransactionActive() {
        var cache = Objects.requireNonNull(cacheManager.getCache("categories"));
        cache.put("all", "stale");
        TransactionSynchronizationManager.initSynchronization();

        cache.evict("all");
        var delegateCache = Objects.requireNonNull(delegate.getCache("categories"));
        assertThat(delegateCache.get("all")).isNotNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(delegateCache.get("all")).isNull();
    }

    @Test
    void swallowsEvictFailureAndIncrementsErrorCounter() {
        Cache throwingCache = new Cache() {
            @Override
            public String getName() {
                return "categories";
            }

            @Override
            public Object getNativeCache() {
                return this;
            }

            @Override
            public @Nullable ValueWrapper get(Object key) {
                return null;
            }

            @Override
            public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
                return null;
            }

            @Override
            public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
                return null;
            }

            @Override
            public @Nullable CompletableFuture<?> retrieve(Object key) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
                return valueLoader.get();
            }

            @Override
            public void put(Object key, @Nullable Object value) {}

            @Override
            public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
                return null;
            }

            @Override
            public void evict(Object key) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void clear() {}
        };
        CacheManager throwingDelegate = new CacheManager() {
            @Override
            public @Nullable Cache getCache(String name) {
                return throwingCache;
            }

            @Override
            public List<String> getCacheNames() {
                return List.of("categories");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EvictSafeCacheManager manager = new EvictSafeCacheManager(throwingDelegate, registry);
        TransactionSynchronizationManager.initSynchronization();

        var cache = Objects.requireNonNull(manager.getCache("categories"));
        cache.evict("all");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(registry.get("cache.evict.errors")
                        .tag("cache.name", "categories")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }
}
