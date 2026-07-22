package com.commerce.batch.event.listener;

import com.commerce.cart.service.CartModifier;
import com.commerce.order.event.OrderPaid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderPaid}를 소비해 주문된 변형 라인을 장바구니에서 비우는 리스너다.
 *
 * <p>아웃박스 릴레이가 커밋된 이벤트만 재발행하므로 커밋 후 단계 대기 없이 평 리스너로 소비하고, 소비
 * 쓰기는 자체 트랜잭션으로 커밋한다. 재전달(at-least-once)은 제거의 자연 멱등이 흡수한다.
 */
@Component
public class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final CartModifier cartModifier;

    public OrderPaidListener(CartModifier cartModifier) {
        this.cartModifier = cartModifier;
    }

    /** 결제 완료 이벤트를 받아 주문된 변형 라인을 장바구니에서 제거한다. */
    @EventListener
    @Transactional
    public void on(OrderPaid event) {
        try {
            cartModifier.removeItems(event.memberId(), event.orderedVariantIds());
        } catch (RuntimeException e) {
            // 장바구니 비우기는 정합성 비필수라 소비 실패를 삼키고 로그만 남긴다.
            log.warn("OrderPaid 소비 실패 — 장바구니 비우기 건너뜀: orderId={}", event.orderId(), e);
        }
    }
}
