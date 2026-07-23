package com.commerce.query.order;

import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.UUID;

/** 관리자 주문 검색 결과 한 건의 경계 모델이다. */
public record OrderSearchInfo(
        UUID orderId,
        String orderNumber,
        UUID memberId,
        String memberEmail,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        Money payAmount,
        Instant orderedAt) {}
