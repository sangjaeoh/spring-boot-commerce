package com.commerce.event.order;

import com.commerce.common.event.event.DomainEvent;
import java.util.Set;
import java.util.UUID;

/**
 * 주문 결제 완료 도메인 이벤트다.
 *
 * @param orderedVariantIds 주문된 라인의 변형 식별자
 */
public record OrderPaid(UUID orderId, UUID memberId, Set<UUID> orderedVariantIds) implements DomainEvent {

    /** 논리 타입 키 — 아웃박스 행 기록과 릴레이 해석 레지스트리 배선이 공유한다. */
    public static final String EVENT_TYPE = "order.OrderPaid";

    public OrderPaid {
        orderedVariantIds = Set.copyOf(orderedVariantIds);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
