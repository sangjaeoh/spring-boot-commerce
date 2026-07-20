package com.commerce.order.event;

import com.commerce.messaging.event.DomainEvent;
import java.util.Set;
import java.util.UUID;

/**
 * 주문 결제 완료 도메인 이벤트다.
 *
 * @param orderedVariantIds 주문된 라인의 변형 식별자
 */
public record OrderPaid(UUID orderId, UUID memberId, Set<UUID> orderedVariantIds) implements DomainEvent {

    public OrderPaid {
        orderedVariantIds = Set.copyOf(orderedVariantIds);
    }
}
