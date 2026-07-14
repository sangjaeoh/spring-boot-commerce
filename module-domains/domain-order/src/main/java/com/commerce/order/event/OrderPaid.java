package com.commerce.order.event;

import java.util.Set;
import java.util.UUID;

/**
 * 주문 결제 완료 도메인 이벤트다. 최소 페이로드로 소비자(장바구니 비우기)가 필요한 것만 담는다.
 *
 * @param orderedVariantIds 비울 라인의 변형들
 */
public record OrderPaid(UUID orderId, UUID memberId, Set<UUID> orderedVariantIds) {

    public OrderPaid {
        orderedVariantIds = Set.copyOf(orderedVariantIds);
    }
}
