package com.commerce.infra.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.commerce.messaging.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class InProcessMessagePublisherTest {

    @Test
    @DisplayName("발행한 도메인 이벤트를 스프링 이벤트 퍼블리셔에 그대로 위임한다")
    void publishDelegatesToApplicationEventPublisher() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        InProcessMessagePublisher publisher = new InProcessMessagePublisher(delegate);
        DomainEvent event = new DomainEvent() {};

        publisher.publish(event);

        verify(delegate).publishEvent(event);
    }
}
