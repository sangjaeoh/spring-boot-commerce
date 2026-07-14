package com.commerce.infra.messaging;

import com.commerce.messaging.event.DomainEvent;
import com.commerce.messaging.publish.MessagePublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트를 스프링 {@link ApplicationEventPublisher}로 재발행하는 in-process transport 어댑터다.
 *
 * <p>같은 애플리케이션 컨텍스트의 리스너가 이벤트를 소비한다. 발행이 쓰기 트랜잭션 안에서 일어나면
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 소비자는 커밋 후에만 통지받는다. 무손실·내구성
 * 보장은 없다(in-process 기준선).
 */
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
