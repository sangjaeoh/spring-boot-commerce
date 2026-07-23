package com.commerce.common.event.publish;

import com.commerce.common.event.event.DomainEvent;

/** 도메인 이벤트를 구독자에게 발행하는 벤더 중립 포트다. */
public interface MessagePublisher {

    /** 도메인 이벤트를 발행한다. */
    void publish(DomainEvent event);
}
