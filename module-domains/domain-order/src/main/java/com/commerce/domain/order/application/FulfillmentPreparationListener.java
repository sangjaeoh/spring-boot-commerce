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
 * <p>클래스명은 {@code OrderPaidListener} 관례를 따르지 않는다 — domain-cart가 이미 같은 이벤트에 그
 * 이름을 쓰고 있어, 둘을 함께 임베드하는 앱(app-admin 등)에서 컴포넌트 스캔 시 단순 클래스명 기반 빈
 * 이름이 충돌한다(패키지가 달라도 Spring 기본 빈 이름은 단순 클래스명이다).
 *
 * <p>아웃박스 릴레이가 커밋된 이벤트만 재발행하므로 커밋 후 단계 대기 없이 평 리스너로 소비하고, 소비
 * 쓰기는 자체 트랜잭션으로 커밋한다. 재전달(at-least-once)은 존재 확인 후 생성으로 멱등을 지킨다.
 */
@Component
class FulfillmentPreparationListener {

    private final FulfillmentRepository fulfillmentRepository;

    FulfillmentPreparationListener(FulfillmentRepository fulfillmentRepository) {
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
