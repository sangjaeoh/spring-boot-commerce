package com.commerce.messaging.publish;

import com.commerce.messaging.event.DomainEvent;

/**
 * 도메인 이벤트를 구독자에게 발행하는 벤더 중립 포트다. transport 구현은 infra가 담당한다.
 */
public interface MessagePublisher {

    /** 도메인 이벤트를 발행한다. */
    void publish(DomainEvent event);
}
