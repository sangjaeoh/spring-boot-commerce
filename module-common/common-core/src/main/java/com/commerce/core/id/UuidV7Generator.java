package com.commerce.core.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 시간 정렬(time-ordered) UUIDv7을 생성한다(RFC 9562).
 *
 * <p>상위 48비트에 Unix epoch 밀리초를, 나머지에 난수를 채워 삽입 지역성이 있는 정렬 가능한
 * 식별자를 만든다.
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_7 = 0x7L;
    private static final long VARIANT_RFC = 0x2L;
    private static final long MILLIS_MASK = 0xFFFFFFFFFFFFL;
    private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private UuidV7Generator() {}

    public static UUID generate() {
        long millis = System.currentTimeMillis() & MILLIS_MASK;
        long randA = RANDOM.nextInt(0x1000);
        long randB = RANDOM.nextLong() & RAND_B_MASK;

        long mostSignificantBits = (millis << 16) | (VERSION_7 << 12) | randA;
        long leastSignificantBits = (VARIANT_RFC << 62) | randB;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
