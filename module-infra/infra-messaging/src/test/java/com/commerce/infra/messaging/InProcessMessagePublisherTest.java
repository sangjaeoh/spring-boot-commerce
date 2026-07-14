package com.commerce.infra.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.commerce.messaging.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class InProcessMessagePublisherTest {

    @Test
    void publishDelegatesToApplicationEventPublisher() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        InProcessMessagePublisher publisher = new InProcessMessagePublisher(delegate);
        DomainEvent event = new DomainEvent() {};

        publisher.publish(event);

        verify(delegate).publishEvent(event);
    }
}
