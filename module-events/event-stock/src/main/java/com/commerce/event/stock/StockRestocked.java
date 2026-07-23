package com.commerce.event.stock;

import com.commerce.common.event.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 품절 재고의 판매 가능 전환(재입고) 도메인 이벤트다.
 *
 * @param eventId 발행 측이 생성한 이벤트 식별자 — 소비 멱등 처리의 dedup 키
 * @param variantId 재입고된 변형 식별자
 * @param occurredAt 발행 측이 기록한 발생 시각
 */
public record StockRestocked(UUID eventId, UUID variantId, Instant occurredAt) implements DomainEvent {

    /** 논리 타입 키 — 아웃박스 행 기록과 릴레이 해석 레지스트리 배선이 공유한다. */
    public static final String EVENT_TYPE = "stock.StockRestocked";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
