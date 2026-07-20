package com.commerce.infra.messaging;

import com.commerce.messaging.event.DomainEvent;
import com.commerce.messaging.publish.MessagePublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 도메인 이벤트를 스프링 {@link ApplicationEventPublisher}로 재발행하는 in-process transport 어댑터다. */
@Component
public final class InProcessMessagePublisher implements MessagePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public InProcessMessagePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
