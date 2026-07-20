package com.commerce.core.id;

import java.security.SecureRandom;
import java.util.UUID;

/** 시간 정렬(time-ordered) UUIDv7(RFC 9562) 생성기다. */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_7 = 0x7L;
    private static final long VARIANT_RFC = 0x2L;
    private static final long MILLIS_MASK = 0xFFFFFFFFFFFFL;
    private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private UuidV7Generator() {}

    /** 새 UUIDv7 값을 만든다. */
    public static UUID generate() {
        // 상위 48비트가 Unix epoch 밀리초라 값 순서가 곧 생성 시각 순서고, 그래서 인덱스 삽입 지역성이 생긴다.
        long millis = System.currentTimeMillis() & MILLIS_MASK;
        long randA = RANDOM.nextInt(0x1000);
        long randB = RANDOM.nextLong() & RAND_B_MASK;

        long mostSignificantBits = (millis << 16) | (VERSION_7 << 12) | randA;
        long leastSignificantBits = (VARIANT_RFC << 62) | randB;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
