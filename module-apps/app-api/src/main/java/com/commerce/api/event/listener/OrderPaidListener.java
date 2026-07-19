package com.commerce.api.event.listener;

import com.commerce.cart.service.CartModifier;
import com.commerce.order.event.OrderPaid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** {@link OrderPaid}를 소비해 주문된 변형 라인을 장바구니에서 비우는 리스너다. */
@Component
public class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final CartModifier cartModifier;

    public OrderPaidListener(CartModifier cartModifier) {
        this.cartModifier = cartModifier;
    }

    /** 결제 완료 커밋 후 주문된 변형 라인을 장바구니에서 제거한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // 커밋 후 단계에는 방금 커밋된 트랜잭션의 동기화가 아직 살아 있어, 새 트랜잭션 없이 도메인 쓰기를 하면
    // 재커밋되지 않고 유실된다. REQUIRES_NEW로 별도 트랜잭션을 연다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderPaid event) {
        try {
            cartModifier.removeItems(event.memberId(), event.orderedVariantIds());
        } catch (RuntimeException e) {
            // 장바구니 비우기는 정합성 비필수라 소비 실패를 삼키고 로그만 남긴다.
            log.warn("OrderPaid 소비 실패 — 장바구니 비우기 건너뜀: orderId={}", event.orderId(), e);
        }
    }
}
