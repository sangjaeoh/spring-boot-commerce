package com.commerce.domain.order.application;

import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.event.order.OrderPaid;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderPaid}를 소비해 이행 애그리거트를 준비 중으로 생성하는 소비자다.
 *
 * <p>아웃박스 릴레이가 커밋된 이벤트만 재발행하므로 커밋 후 단계 대기 없이 평 리스너로 소비하고, 소비
 * 쓰기는 자체 트랜잭션으로 커밋한다. 재전달(at-least-once)은 존재 확인 후 생성으로 멱등을 지킨다.
 */
@Component
class OrderPaidListener {

    private final FulfillmentRepository fulfillmentRepository;

    OrderPaidListener(FulfillmentRepository fulfillmentRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
    }

    /** 결제 완료 이벤트를 받아 이행을 준비 중으로 생성한다. 이미 생성됐으면 아무것도 하지 않는다. */
    @EventListener
    @Transactional
    public void on(OrderPaid event) {
        if (fulfillmentRepository.findByOrderId(event.orderId()).isPresent()) {
            return;
        }
        fulfillmentRepository.save(Fulfillment.create(event.orderId()));
    }
}
