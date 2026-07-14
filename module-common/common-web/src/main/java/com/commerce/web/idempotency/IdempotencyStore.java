package com.commerce.web.idempotency;

/**
 * 멱등 키의 진행·완료를 기록하는 벤더 중립 저장소 포트다.
 *
 * <p>인메모리 기본 구현을 두고, 다중 인스턴스가 필요하면 infra가 분산 구현(예: Redis)을 제공한다.
 */
public interface IdempotencyStore {

    /**
     * 키의 처리를 시작으로 예약한다.
     *
     * @return 이 호출이 키를 처음 선점했으면 {@code true}, 이미 진행 중이거나 최근 완료된 키(창 이내)면
     *     {@code false}
     */
    boolean tryBegin(String key);

    /** 키의 처리를 완료로 표시해, 창(TTL) 동안 재요청을 계속 거부하게 한다. */
    void complete(String key);
}
