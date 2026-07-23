package com.commerce.domain.payment.adapter.integration;

import java.util.UUID;

/** fake PG의 승인 응답 유실을 나타내는 예외다 — 호출자에겐 벤더 SDK의 타임아웃과 동일하게 관측된다. */
public class FakeGatewayTimeoutException extends RuntimeException {

    public FakeGatewayTimeoutException(UUID paymentId) {
        super("승인 응답이 유실됐다(시뮬레이션): paymentId=" + paymentId);
    }
}
