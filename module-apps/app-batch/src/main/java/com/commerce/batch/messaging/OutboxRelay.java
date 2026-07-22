package com.commerce.batch.messaging;

import com.commerce.event.event.DomainEvent;
import com.commerce.event.outbox.OutboxMessage;
import com.commerce.event.outbox.OutboxStore;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 아웃박스에 커밋된 미발행 도메인 이벤트를 in-process로 재발행하는 릴레이 잡이다.
 *
 * <p>발행 도메인은 커밋과 원자적으로 행만 남기고(OutboxMessagePublisher), 전달은 이 릴레이가 커밋된 행을
 * 생성순으로 읽어 수행한다. 발행 후 표시 순서라 크래시 시 재전달(at-least-once)이고, 소비 멱등이 중복을
 * 흡수한다.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    // 한 주기의 폴링 상한 — 적체 시에도 주기당 작업량을 예측 가능하게 묶고, 잔량은 다음 주기가 이어간다.
    private static final int BATCH_LIMIT = 100;

    private final OutboxStore outboxStore;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxStore outboxStore, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.outboxStore = outboxStore;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /** 미발행 이벤트를 생성순으로 재발행하고 발행 표시한다. 노드 간 동시 실행은 분산 락이 하나로 줄인다. */
    @Scheduled(fixedDelayString = "${messaging.outbox.relay.fixed-delay}")
    // lockAtMostFor는 릴레이 소요 상한이다 — DB 폴링·in-process 재발행뿐이라 짧게 둔다.
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    public void relay() {
        for (OutboxMessage message : outboxStore.fetchUnpublished(BATCH_LIMIT)) {
            try {
                eventPublisher.publishEvent(deserialize(message));
                outboxStore.markPublished(message.id());
            } catch (RuntimeException e) {
                // 한 이벤트의 실패가 릴레이를 중단시키지 않는다 — 남은 이벤트를 계속 처리하고 다음 주기가 재시도한다.
                log.warn("아웃박스 이벤트 재발행 실패: id={} type={}", message.id(), message.eventType(), e);
            }
        }
    }

    /** FQCN으로 이벤트 타입을 복원해 페이로드를 역직렬화한다. */
    private DomainEvent deserialize(OutboxMessage message) {
        try {
            Class<? extends DomainEvent> type =
                    Class.forName(message.eventType()).asSubclass(DomainEvent.class);
            return objectMapper.readValue(message.payload(), type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("아웃박스 이벤트 타입을 찾을 수 없다: " + message.eventType(), e);
        }
    }
}
