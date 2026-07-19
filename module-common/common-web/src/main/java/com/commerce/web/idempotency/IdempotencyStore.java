package com.commerce.web.idempotency;

/** 멱등 키의 진행·완료를 기록하는 벤더 중립 저장소 포트다. */
public interface IdempotencyStore {

    /**
     * 키의 처리를 시작으로 예약한다. 이 호출이 키를 처음 선점했으면 {@code true}, 이미 진행 중이거나 최근
     * 완료된 키(창 이내)면 {@code false}다.
     *
     * @throws RuntimeException 저장소에 접근할 수 없으면 전파한다(fail-closed) — 호출자는 중복 차단 없이
     *     요청을 통과시키지 않고 거부한다
     */
    boolean tryBegin(String key);

    /** 키의 처리를 완료로 표시해, 창(TTL) 동안 재요청을 계속 거부하게 한다. */
    void complete(String key);
}
