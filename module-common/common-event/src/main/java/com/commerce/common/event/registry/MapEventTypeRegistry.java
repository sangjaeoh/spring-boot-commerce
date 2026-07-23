package com.commerce.common.event.registry;

import com.commerce.common.event.event.DomainEvent;
import java.util.Map;

/** 배선 시점에 고정한 맵으로 논리 타입 키를 해석하는 {@link EventTypeRegistry} 구현이다. */
public final class MapEventTypeRegistry implements EventTypeRegistry {

    private final Map<String, Class<? extends DomainEvent>> mappings;

    public MapEventTypeRegistry(Map<String, Class<? extends DomainEvent>> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public Class<? extends DomainEvent> resolve(String eventType) {
        Class<? extends DomainEvent> type = mappings.get(eventType);
        if (type == null) {
            throw new IllegalStateException("미등록 이벤트 타입 키다: " + eventType);
        }
        return type;
    }
}
