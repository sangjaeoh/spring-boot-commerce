package com.commerce.web.idempotency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * 웹 하네스 테스트용 단일 인스턴스 인메모리 {@link IdempotencyStore} 구현이다. 실행 앱은 Redis 구현
 * (infra-redis)을 쓴다.
 */
@Component
final class InMemoryIdempotencyStore implements IdempotencyStore {

    // in-flight 락 수명. 최대 요청 처리 시간(동기 결제 등)·클라이언트/프록시 타임아웃 재시도 지연을 넘도록
    // 넉넉히 잡는다 — 그래야 타임아웃 재시도가 원본 처리 중에 락 자기만료로 새로 획득하지 못한다. 완료 없이
    // 이 시간이 지나면(크래시 등) 키를 회수한다.
    private static final long DEFAULT_IN_FLIGHT_MILLIS = 300_000L;
    // 완료 후 재요청을 거부하는 dedup 창. 더블서밋(즉시 재클릭)·즉시 재시도를 흡수한다.
    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, Long> expiryByKey = new ConcurrentHashMap<>();
    private final LongSupplier millisSource;
    private final long inFlightMillis;
    private final long windowMillis;

    InMemoryIdempotencyStore() {
        this(System::currentTimeMillis, DEFAULT_IN_FLIGHT_MILLIS, DEFAULT_WINDOW_MILLIS);
    }

    InMemoryIdempotencyStore(LongSupplier millisSource, long inFlightMillis, long windowMillis) {
        this.millisSource = millisSource;
        this.inFlightMillis = inFlightMillis;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryBegin(String key) {
        long now = millisSource.getAsLong();
        evictExpiredIfLarge(now);
        boolean[] acquired = {false};
        expiryByKey.compute(key, (ignoredKey, existingExpiry) -> {
            if (existingExpiry == null || existingExpiry <= now) {
                acquired[0] = true;
                return now + inFlightMillis;
            }
            return existingExpiry;
        });
        return acquired[0];
    }

    @Override
    public void complete(String key) {
        // 완료 시 in-flight 락을 짧은 dedup 창으로 단축한다.
        expiryByKey.put(key, millisSource.getAsLong() + windowMillis);
    }

    private void evictExpiredIfLarge(long now) {
        if (expiryByKey.size() > MAX_ENTRIES) {
            expiryByKey.values().removeIf(expiry -> expiry <= now);
        }
    }
}
