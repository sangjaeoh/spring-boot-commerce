package com.commerce.event.registry;

import com.commerce.event.event.DomainEvent;

/** 아웃박스 행의 논리 타입 키를 이벤트 클래스로 해석하는 계약이다. 릴레이 실행 앱이 구현을 배선한다. */
public interface EventTypeRegistry {

    /**
     * 논리 타입 키의 이벤트 클래스를 반환한다.
     *
     * @throws IllegalStateException 미등록 키면
     */
    Class<? extends DomainEvent> resolve(String eventType);
}
