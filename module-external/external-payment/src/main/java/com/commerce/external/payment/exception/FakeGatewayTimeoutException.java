package com.commerce.external.payment.exception;

import java.util.UUID;

/**
 * fake PG의 승인 응답 유실 시뮬레이션이다. 승인 거래는 PG에 기록된 채 호출자만 결과를 잃는다 — 호출자에겐
 * 벤더 SDK의 타임아웃과 동일하게 관측된다.
 */
public class FakeGatewayTimeoutException extends RuntimeException {

    public FakeGatewayTimeoutException(UUID paymentId) {
        super("승인 응답이 유실됐다(시뮬레이션): paymentId=" + paymentId);
    }
}
