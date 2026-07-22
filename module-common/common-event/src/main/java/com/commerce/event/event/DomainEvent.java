package com.commerce.event.event;

/**
 * {@link com.commerce.event.publish.MessagePublisher}로 발행 가능한 도메인 이벤트의 마커다.
 */
public interface DomainEvent {

    /** 아웃박스 행 기록과 릴레이 해석에 쓰는 논리 타입 키({@code {도메인}.{이벤트명}})를 반환한다. */
    String eventType();
}
